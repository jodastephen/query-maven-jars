/*
 *  Copyright 20174-present Stephen Colebourne
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.joda.queryjars;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Query metadata in jar files.
 */
public class QueryJars {

    static int total = 0;
    static int valid = 0;
    static List<String> found = new ArrayList<>();
    static List<String> maybe = new ArrayList<>();
    static List<String> none = new ArrayList<>();
    
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("C:\\Users\\Stephen\\.m2\\repository");
        
        Files.walkFileTree(path, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.endsWith(".cache") || dir.endsWith(".locks")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path fileName = file.getFileName();
                if (fileName.toString().endsWith(".jar") &&
                        !fileName.toString().contains("SNAPSHOT") &&
                        !fileName.toString().endsWith("-sources.jar") &&
                        !fileName.toString().endsWith("-javadoc.jar")) {
                    
                    try (FileSystem zipfs = FileSystems.newFileSystem(file, QueryJars.class.getClassLoader())) {
                        Path pathInZipfile = zipfs.getPath("META-INF/MANIFEST.MF");
                        if (Files.exists(pathInZipfile)) {
                            Set<String> rootPkg = findRootPackages(zipfs.getPath("/"));
                            total++;
                            byte[] bytes = Files.readAllBytes(pathInZipfile);
                            String str = new String(bytes, StandardCharsets.UTF_8);
                            String processed = processManifest(str, rootPkg, file.getFileName());
                            assert processed != null;
//                            System.out.println(processed + " - " + file.toString());
//                            System.out.println();
                        } else {
                            System.out.println("No META-INF/MANIFEST.MF - " + file.toString());
                        }
                    } 
                    
                    return FileVisitResult.SKIP_SIBLINGS;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        
        found.sort(Comparator.naturalOrder());
        maybe.sort(Comparator.naturalOrder());
        none.sort(Comparator.naturalOrder());
        
        System.out.println("Total: " + valid + "/" + total);
        System.out.println("");
        System.out.println("VALID");
        found.forEach(s -> {System.out.println(s);});
        System.out.println("");
        System.out.println("MAYBE");
        maybe.forEach(s -> {System.out.println(s);});
        System.out.println("");
        System.out.println("NONE");
        none.forEach(s -> {System.out.println(s);});
    }

    private static Set<String> findRootPackages(Path path) throws IOException {
        Set<Path> set = new HashSet<>();
        Files.walkFileTree(path, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith(".class")) {
                    Path dir = file.getParent();
                    if (set.size() == 0 || dir.getNameCount() == set.iterator().next().getNameCount()) {
                        set.add(dir);
                    } else if (dir.getNameCount() < set.iterator().next().getNameCount()) {
                        set.clear();
                        set.add(dir);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        return set.stream()
                .map(f -> f.toString().replace('/', '.'))
                .map(s -> s.startsWith(".") ? s.substring(1) : s)
                .collect(Collectors.toSet());
    }

    private static String processManifest(String str, Set<String> rootPkg, Path path) {
        str = str.replace("\r\n", "\n");
        List<String> list = ImmutableList.copyOf(Splitter.on("\n").split(str));
        list = list.stream()
                .filter(s -> !s.startsWith(" "))
                .map(s -> s.trim())
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
        
        Map<String, String> map = list.stream()
                .filter(s -> s.contains(":"))
                .collect(Collectors.toMap(
                        s -> s.substring(0, s.indexOf(':')).trim(),
                        s -> s.substring(s.indexOf(':') + 1).trim(),
                        (a, b) -> a));
        
        if (map.containsKey("Bundle-SymbolicName")) {
            String name = map.get("Bundle-SymbolicName");
            if (name.contains(";")) {
                name = name.substring(0, name.indexOf(';'));
            }
            if (name.contains(".")) {
                if (name.matches("[a-z0-9.]+") && noDuplicateSegments(name)) {
                    valid++;
                    found.add(name + " (from Bundle-SymbolicName) " + rootPkg + " " + path);
                } else {
                    maybe.add(name + " (from Bundle-SymbolicName) " + rootPkg + " " + path);
                }
                return name + " - " + "Bundle-SymbolicName";
            } else {
//                System.out.println("REJECTED: " + name + " - " + "Bundle-SymbolicName");
            }
        }
        if (map.containsKey("Package")) {
            String pkg = map.get("Package");
            if (pkg.matches("[a-z0-9.]+") && pkg.contains(".")) {
                valid++;
                found.add(pkg + " (from Package) " + rootPkg + " " + path);
                return pkg + " - " + "Package";
            } else {
//                System.out.println("REJECTED: " + pkg + " - " + "Package");
            }
        }
        if (map.containsKey("Implementation-Title")) {
            String title = map.get("Implementation-Title");
            if (title.matches("[a-z0-9.]+") && title.contains(".")) {
                valid++;
                found.add(title + " (from Implementation-Title) " + rootPkg + " " + path);
                return title + " - " + "Implementation-Title";
            } else {
//                System.out.println("REJECTED: " + title + " - " + "Implementation-Title");
            }
        }
        // remove useless stuff to help find useful info
        Map<String, String> reducedMap = new HashMap<String, String>(map);
        reducedMap.remove("Created-By");
        reducedMap.remove("Originally-Created-By");
        reducedMap.remove("Built-By");
        reducedMap.remove("Built-Date");
        reducedMap.remove("Build-Jdk");
        reducedMap.remove("Build-Number");
        reducedMap.remove("Build-Version");
        reducedMap.remove("Java-Bean");
        reducedMap.remove("Can-Redefine-Classes");
        reducedMap.remove("Class-Path");
        reducedMap.remove("Boot-Class-Path");
        reducedMap.remove("Sealed");
        reducedMap.remove("Comment");
        reducedMap.remove("url");
        reducedMap.remove("Licence-Url");
        reducedMap.remove("SHA-Digest");
        reducedMap.remove("SHA1-Digest");
        reducedMap.remove("MD5-Digest");
        reducedMap.remove("Digest-Algorithms");
        reducedMap.remove("X-Compile-Source");
        reducedMap.remove("X-Compile-Source-JDK");
        reducedMap.remove("X-Compile-Target");
        reducedMap.remove("X-Compile-Target-JDK");
        reducedMap.remove("Manifest-Version");
        reducedMap.remove("Implementation-Version");
        reducedMap.remove("Implementation-Vendor");
        reducedMap.remove("Implementation-Vendor-Id");
        reducedMap.remove("Implementation-Date");
        reducedMap.remove("Implementation-Build");
        reducedMap.remove("Implementation-Build-Id");
        reducedMap.remove("Implementation-URL");
        reducedMap.remove("Specification-Version");
        reducedMap.remove("Specification-Vendor");
        reducedMap.remove("Ant-Version");
        reducedMap.remove("Archiver-Version");
        reducedMap.remove("Profile");
        reducedMap.remove("Include-Resource");
        reducedMap.remove("DynamicImport-Package");
        reducedMap.remove("Import-Package");
        reducedMap.remove("Export-Package");
        reducedMap.remove("Bundle-Activator");
        reducedMap.remove("Bundle-ClassPath");
        reducedMap.remove("Bundle-Name");
        reducedMap.remove("Bundle-Version");
        reducedMap.remove("Bundle-Vendor");
        reducedMap.remove("Bundle-ManifestVersion");
        reducedMap.remove("Bundle-License");
        reducedMap.remove("Bundle-RequiredExecutionEnvironment");
        reducedMap.remove("Bnd-LastModified");
        reducedMap.remove("Dynamic-Import");
        reducedMap.remove("Eclipse-BuddyPolicy");
        
        none.add(path.toString() + " - " + reducedMap);
        return "*** " +  map.toString(); //map.toString();
    }

    private static boolean noDuplicateSegments(String name) {
        List<String> segments = Splitter.on('.').splitToList(name);
        return ImmutableSet.copyOf(segments).size() == segments.size();
    }

}
