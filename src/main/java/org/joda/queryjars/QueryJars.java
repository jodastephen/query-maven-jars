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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Query metadata in jar files.
 */
public class QueryJars {

    static int total = 0;
    static int valid = 0;
    static List<String> found = new ArrayList<>();
    static List<String> maybe = new ArrayList<>();
    
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
                            total++;
                            byte[] bytes = Files.readAllBytes(pathInZipfile);
                            System.out.println(processManifest(new String(bytes, StandardCharsets.UTF_8)) +
                                    " - " + file.toString());
                            System.out.println();
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
        
        System.out.println("Total: " + valid + "/" + total);
        System.out.println("");
        System.out.println("VALID");
        found.forEach(s -> {System.out.println(s);});
        System.out.println("");
        System.out.println("MAYBE");
        maybe.forEach(s -> {System.out.println(s);});
    }

    private static String processManifest(String str) {
        str = str.replace("\r\n", "\n");
        List<String> list = ImmutableList.copyOf(Splitter.on("\n").split(str));
        list = list.stream().map(s -> s.trim()).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        
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
                valid++;
                if (name.matches("[a-z0-9.]+")) {
                    found.add(name + " - " + "Bundle-SymbolicName");
                } else {
                    maybe.add(name + " - " + "Bundle-SymbolicName");
                }
                return name + " - " + "Bundle-SymbolicName";
            } else {
                System.out.println("REJECTED: " + name + " - " + "Bundle-SymbolicName");
            }
        }
        if (map.containsKey("Package")) {
            String pkg = map.get("Package");
            if (pkg.equals(pkg.toLowerCase()) && pkg.matches("[a-z0-9.]+") && pkg.contains(".")) {
                valid++;
                found.add(pkg + " - " + "Package");
                return pkg + " - " + "Package";
            } else {
                System.out.println("REJECTED: " + pkg + " - " + "Package");
            }
        }
        if (map.containsKey("Implementation-Title")) {
            String title = map.get("Implementation-Title");
            if (title.equals(title.toLowerCase()) && title.matches("[a-z0-9.]+") && title.contains(".")) {
                valid++;
                found.add(title + " - " + "Implementation-Title");
                return title + " - " + "Implementation-Title";
            } else {
                System.out.println("REJECTED: " + title + " - " + "Implementation-Title");
            }
        }
        return "*** " +  map.toString(); //map.toString();
    }

}
