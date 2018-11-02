/*
 * Copyright 2018 Matthew Lee
 *
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

package io.github.mthli.nongfu;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

abstract class BaseAction extends AnAction {
    abstract boolean isEnable(@Nonnull AnActionEvent event);
    @Nonnull abstract String provideDialogTitleText();
    @Nonnull abstract String provideDialogActionOkText();
    abstract void onDialogActionOkInvoked(
            @Nonnull Project project, @Nonnull Queue<VirtualFile> queue,
            @Nonnull String currentModulePath, @Nonnull String targetModulePath);

    private Module mCurrentModule = null;
    private Module mTargetModule = null;

    @Override
    public final void update(@Nonnull AnActionEvent event) {
        // Disable when no project
        Project project = event.getProject();
        Presentation presentation = event.getPresentation();
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }

        // Disable when less than 2 modules
        List<Module> moduleList = getModuleList(project);
        if (moduleList.size() < 2) {
            presentation.setEnabled(false);
            return;
        }

        // Disable when no selected file
        Queue<VirtualFile> selectedFileQueue = getSelectedFileQueue(event.getDataContext());
        if (selectedFileQueue.isEmpty()) {
            presentation.setEnabled(false);
            return;
        }

        // Disable when selected file not located in same module
        for (Module module : moduleList) {
            boolean hasMatch = false;
            for (VirtualFile file : selectedFileQueue) {
                if (file.getPath().startsWith(getModulePath(module))) {
                    hasMatch = true;
                } else {
                    if (hasMatch) {
                        presentation.setEnabled(false);
                        return;
                    }
                }
            }
        }

        presentation.setEnabled(isEnable(event));
    }

    @Override
    public final void actionPerformed(@Nonnull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        List<Module> moduleList = getModuleList(project);
        Queue<VirtualFile> selectedFileQueue = getSelectedFileQueue(event.getDataContext());
        for (Module module : moduleList) {
            String modulePath = getModulePath(module);
            for (VirtualFile file : selectedFileQueue) {
                if (file.getPath().startsWith(modulePath)) {
                    mCurrentModule = module;
                    break;
                }
            }
        }

        JPanel panel = new JPanel();
        panel.add(buildComboBoxOfModuleList(project));
        DialogBuilder builder = new DialogBuilder()
                .title(provideDialogTitleText())
                .centerPanel(panel);

        builder.addCancelAction();
        builder.addOkAction().setText(provideDialogActionOkText());
        builder.setOkOperation(() -> {
            builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
            onDialogActionOkInvoked(project, selectedFileQueue,
                    getModulePath(mCurrentModule), getModulePath(mTargetModule));
        });

        builder.show();
    }

    @Nonnull
    private List<Module> getModuleList(@Nonnull Project project) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        return Arrays.stream(modules)
                .filter(module -> !module.getName().equals(project.getName()))
                .collect(Collectors.toList());
    }

    @Nonnull
    private ComboBox<String> buildComboBoxOfModuleList(@Nonnull Project project) {
        List<Module> moduleList = getModuleList(project);
        moduleList.remove(mCurrentModule);

        ComboBox<String> comboBox = new ComboBox<>();
        moduleList.stream()
                .map(module -> "Module: '" + module.getName() + "'")
                .forEach(comboBox::addItem);

        // Remember latest selection
        if (mTargetModule == null || !moduleList.contains(mTargetModule)) {
            mTargetModule = moduleList.get(0);
            comboBox.setSelectedIndex(0);
        } else {
            comboBox.setSelectedIndex(moduleList.indexOf(mTargetModule));
        }

        comboBox.addActionListener(e -> {
            int index = ((ComboBox) e.getSource()).getSelectedIndex();
            mTargetModule = moduleList.get(index);
        });

        comboBox.setMinimumAndPreferredWidth(360);
        return comboBox;
    }

    @Nonnull
    private String getModulePath(@Nonnull Module module) {
        return module.getProject().getBasePath() + "/" + module.getName();
    }

    @Nonnull
    private Queue<VirtualFile> getSelectedFileQueue(@Nonnull DataContext context) {
        VirtualFile[] files = DataKeys.VIRTUAL_FILE_ARRAY.getData(context);
        return new LinkedList<>(files != null ? Arrays.asList(files) : Collections.emptyList());
    }

    @Nullable
    final PsiFile findPsiFile(@Nonnull Project project, @Nonnull VirtualFile file) {
        return PsiManager.getInstance(project).findFile(file);
    }

    @Nullable
    final PsiDirectory findPsiDirectory(@Nonnull Project project, @Nonnull VirtualFile file) {
        return PsiManager.getInstance(project).findDirectory(file);
    }

    @Nullable
    final PsiDirectory mkdirs(@Nonnull Project project, @Nonnull String path) {
        return WriteAction.compute(() -> {
            try {
                return DirectoryUtil.mkdirs(PsiManager.getInstance(project), path);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    final void invokeLater(@Nonnull Project project, @Nonnull Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable,
                ModalityState.defaultModalityState(), project.getDisposed());
    }

    @SuppressWarnings("SameParameterValue")
    final void showErrorHint(@Nonnull Project project, @Nonnull String title, @Nonnull String message) {
        CommonRefactoringUtil.showErrorHint(project, null, title, message, null);
    }
}
