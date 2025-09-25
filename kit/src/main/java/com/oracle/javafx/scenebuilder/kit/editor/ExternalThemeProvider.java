/*
 * Copyright (c) 2024, Gluon and/or its affiliates.
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
 *  - Neither the name of Gluon nor the names of its
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
package com.oracle.javafx.scenebuilder.kit.editor;

import com.oracle.javafx.scenebuilder.kit.editor.EditorPlatform.Theme;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;

public interface ExternalThemeProvider {

    /**
     * Gets a list of possible themes from an external plugin
     *
     * @return list of themes
     */
    default List<Theme> getExternalThemes() {
        return List.of();
    }

    /**
     * Gets a list of possible stylesheets from an external plugin
     *
     * @return a list of stylesheets
     */
    default List<String> getExternalStylesheets() {
        return List.of();
    }

    /**
     * Verifies if a given text (either from a class name or an FXML file) contains
     * package names of classes from a given plugin
     *
     * @param text the text to check
     * @return true if the text contains package names from a plugin
     */
    default boolean hasClassFromExternalPlugin(String text) {
        return false;
    }

    /**
     * If controls from an external plugin are added, but the current theme doesn't support those,
     * it shows an alert that the theme from the plugin needs to be activated
     *
     * @param owner the stage that will own the alert
     * @param currentTheme the current theme
     * @param onSuccess if alert button is accepted, the external plugin will be applied
     */
    default void showThemeAlert(Stage owner, Theme currentTheme, Consumer<Theme> onSuccess) {}

    /**
     * When a jar is imported as custom library, but there is a plugin with such
     * jar, an alert is shown
     *
     * @param owner the stage that will own the alert
     */
    default void showImportAlert(Stage owner) {}

    /**
     * Returns a link to the javadoc URL of the plugin
     *
     * @return a valid link to javadoc
     */
    default String getExternalJavadocURL() {
        return "";
    }
}
