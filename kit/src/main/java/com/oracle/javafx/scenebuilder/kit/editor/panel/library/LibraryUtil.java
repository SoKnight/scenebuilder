/*
 * Copyright (c) 2016, 2024, Gluon and/or its affiliates.
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.javafx.scenebuilder.kit.editor.panel.library;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.module.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LibraryUtil {

    public static final String FOLDERS_LIBRARY_FILENAME = "library.folders"; //NOI18N
    public static final String FXMLS_LIBRARY_FILENAME = "library.fxmls"; //NOI18N
    public static final String JARS_LIBRARY_FILENAME = "library.jars"; //NOI18N

    private static final boolean DYNAMIC_MODULE_PATH_ENABLED;

    LibraryUtil() {
        // no-op
    }

    public static ModuleLayer constructModuleLayer(Collection<Path> modulesOrJarsOrFolders, ClassLoader parentLoader) {
        if (!DYNAMIC_MODULE_PATH_ENABLED)
            return ModuleLayer.boot();

        var files = modulesOrJarsOrFolders.stream()
            .filter(Files::isRegularFile)
            .toList();

        if (files.isEmpty())
            return ModuleLayer.empty();

        var moduleFinder = ModuleFinder.of(files.toArray(Path[]::new));
        var universe = moduleFinder.findAll().stream()
            .map(ModuleReference::descriptor)
            .map(ModuleDescriptor::name)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        var parentConfiguration = ModuleLayer.boot().configuration();
        var configuration = parentConfiguration.resolve(ModuleFinder.of(), moduleFinder, universe);
        var parentLayers = List.of(ModuleLayer.boot());

        var controller = ModuleLayer.defineModulesWithManyLoaders(configuration, parentLayers, parentLoader);
        controller.layer().modules().forEach(controller::enableNativeAccess);
        return controller.layer();
    }

    public static Optional<ModuleReference> getModuleReference(Path path) {
        if (DYNAMIC_MODULE_PATH_ENABLED) {
            var moduleFinder = ModuleFinder.of(path);
            return moduleFinder.findAll().stream().findFirst();
        }

        return ModuleLayer.boot().configuration().modules().stream()
            .map(ResolvedModule::reference)
            .filter(ref -> path.equals(ref.location().map(Path::of).orElse(null)))
            .findFirst();
    }

    public static boolean isJarPath(Path path) {
        final String pathString = path.toString().toLowerCase(Locale.ROOT);
        return pathString.endsWith(".jar"); //NOI18N
    }

    public static boolean isFxmlPath(Path path) {
        final String pathString = path.toString().toLowerCase(Locale.ROOT);
        return pathString.endsWith(".fxml"); //NOI18N
    }

    public static boolean isFolderMarkerPath(Path path) {
        final String pathString = path.toString().toLowerCase(Locale.ROOT);
        return pathString.endsWith(".folders"); //NOI18N
    }

    public static boolean isFxmlMarkerPath(Path path) {
        final String pathString = path.toString().toLowerCase(Locale.ROOT);
        return pathString.endsWith(".fxmls");
    }

    public static boolean isJarMarkerPath(Path path) {
        final String pathString = path.toString().toLowerCase(Locale.ROOT);
        return pathString.endsWith(".jars");
    }

    public static List<Path> getMarkerFilePaths(Path libraryFile, Predicate<Path> pathFilter) throws IOException {
        try (var lines = Files.lines(libraryFile, StandardCharsets.UTF_8)) {
            return lines.map(line -> Path.of(line.trim()))
                .filter(Files::exists)
                .filter(pathFilter)
                .toList();
        }
    }

    static {
        var property = System.getProperty("library.dynamicModulePath", "false");
        DYNAMIC_MODULE_PATH_ENABLED = property.equalsIgnoreCase("true");
    }

}
