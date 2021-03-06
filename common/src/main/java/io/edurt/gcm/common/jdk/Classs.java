/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.edurt.gcm.common.jdk;

import io.edurt.gcm.common.file.Paths;
import io.edurt.gcm.common.utils.ObjectUtils;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.lang.String.format;

public class Classs
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Classs.class);
    public static final String TYPE_FILE = "file";
    public static final String PACKAGE_SCANNER = ".";
    public static final String ENCODE = "UTF-8";
    private static final ClassLoader classLoader = Classs.class.getClassLoader();

    private Classs()
    {}

    /**
     * Scan all class files under the specified package
     *
     * @param scanPackage scan package
     * @return Specify all class files under the package
     */
    public static Set<Class<?>> scanClassInPackage(String scanPackage)
    {
        Set<Class<?>> classes = new HashSet<>();
        try {
            Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(scanPackage.replace(PACKAGE_SCANNER, "/"));
            if (!urls.hasMoreElements()) {
                LOGGER.error("Scan package {} not has controller!", scanPackage);
                urls = scanJarInProject();
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String protocol = url.getProtocol();
                // If it is a file, scan all the files under the package
                if (protocol.equalsIgnoreCase(TYPE_FILE)) {
                    scanClassesInFilePackage(scanPackage, URLDecoder.decode(url.getFile(), ENCODE), classes);
                }
                else {
                    scanClassesInJarPackage(scanPackage, classes, url);
                }
            }
        }
        catch (IOException e) {
            LOGGER.error(format("Scan package %s has error!", scanPackage), e);
        }
        return classes;
    }

    /**
     * Scan all class files in the package path
     *
     * @param packageName scan package name
     * @param packagePath Specific scan packet path
     * @param classes Specify all class files under the package
     */
    private static void scanClassesInFilePackage(String packageName, String packagePath, Set<Class<?>> classes)
    {
        // Get the directory of this package and create a file
        File directory = new File(packagePath);
        if (!directory.exists() || !directory.isDirectory()) {
            LOGGER.error("Scan package {} in jar file not exists!", directory);
            return;
        }
        // If it exists, get all the. Class files and subdirectories under the package
        File[] directoryFiles = directory.listFiles(file -> file.isDirectory() || file.getName().endsWith(".class"));
        if (directoryFiles == null) {
            LOGGER.info("Scan class file from {} is empty!", directory);
            return;
        }
        // Loop to get all class files
        Arrays.asList(directoryFiles).forEach(v -> {
            // If it is a directory, scan recursively
            if (v.isDirectory()) {
                scanClassesInFilePackage(String.join(".", packageName, v.getName()), v.getAbsolutePath(), classes);
            }
            else {
                // If it is a Java class file, remove the following. Class and leave only the class name
                String className = v.getName().substring(0, v.getName().length() - 6);
                try {
                    classes.add(Thread.currentThread().getContextClassLoader().loadClass(packageName + '.' + className));
                }
                catch (ClassNotFoundException e) {
                    LOGGER.error("Load class {} error", className);
                }
            }
        });
    }

    private static void scanClassesInJarPackage(String packageName, Set<Class<?>> classes, URL url)
    {
        packageName = packageName.replace(".", "/");
        JarFile jarFile;
        try {
            LOGGER.info("Load jar file from {}", url);
            JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
            jarFile = jarURLConnection.getJarFile();
        }
        catch (IOException ex) {
            LOGGER.error("Load jar file from {} error", ex);
            return;
        }
        Enumeration<JarEntry> jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry jarEntry = jarEntries.nextElement();
            String jarEntryName = jarEntry.getName();
            if (jarEntryName.contains(packageName) && !jarEntryName.equals(packageName + "/")) {
                if (jarEntry.isDirectory()) {
                    String clazzName = jarEntry.getName().replace("/", ".");
                    int endIndex = clazzName.lastIndexOf(".");
                    String prefix = null;
                    if (endIndex > 0) {
                        prefix = clazzName.substring(0, endIndex);
                    }
                    scanClassesInJarPackage(prefix, classes, url);
                }
                if (jarEntry.getName().endsWith(".class")) {
                    try {
                        Class<?> clazz = classLoader.loadClass(jarEntry.getName().replace("/", ".").replace(".class", ""));
                        classes.add(clazz);
                    }
                    catch (ClassNotFoundException e) {
                        LOGGER.error("Class {} not found from jar file {}", jarEntry.getName(), packageName);
                    }
                }
            }
        }
    }

    /**
     * Scan all jar for project home
     *
     * @return All jar URl
     * @throws IOException
     */
    public static Enumeration<URL> scanJarInProject()
            throws IOException
    {
        LOGGER.info("Scan jar on project home {}", Paths.getProjectHome());
        return classLoader.getResources("META-INF");
    }

    public static Set<Class<?>> scanClassesInProject(Class annotationClazz)
    {
        Set<Class<?>> classes = new HashSet<>();
        Set<URL> jarSets = new HashSet<>();
        try {
            Enumeration<URL> jars = scanJarInProject();
            while (jars.hasMoreElements()) {
                jarSets.add(jars.nextElement());
            }
            URLClassLoader urlClassLoader = new URLClassLoader(jarSets.toArray(new URL[0]));
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forClassLoader(urlClassLoader))
//                    .addClassLoader(Thread.currentThread().getContextClassLoader())
                    .addScanners(new TypeAnnotationsScanner(), new SubTypesScanner(false))
            );
            reflections.getTypesAnnotatedWith(annotationClazz)
                    .parallelStream()
                    .forEach(clazz -> classes.add((Class<?>) clazz));
        }
        catch (Exception ex) {
            LOGGER.error("Scan class on project error", ex);
        }
        return classes;
    }

    public static Set<Class<?>> scanClassesInProjectForClassLoader(Class annotationClazz)
    {
        Set<Class<?>> classes = new HashSet<>();
        try {
            Field field = ClassLoader.class.getDeclaredField("classes");
            field.setAccessible(true);
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Vector<Class> classVector = (Vector<Class>) field.get(classLoader);
            classVector.stream()
                    .filter(v -> !v.isInterface()) // filter interface
                    .filter(v -> ObjectUtils.isNotEmpty(v.getPackage())) // filter proxy package is null
                    .forEach(v -> classes.add(v));
        }
        catch (Exception ex) {
            LOGGER.error("Scan class on project error", ex);
        }
        return classes;
    }
}
