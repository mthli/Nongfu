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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
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

abstract class BaseAction extends AnAction {
    abstract boolean isEnable(@Nonnull AnActionEvent event);
    @Nonnull abstract String provideDialogTitleText();
    @Nonnull abstract String provideDialogActionOkText();
    abstract void onDialogActionOkInvoked(
            @Nonnull AnActionEvent event, @Nonnull Queue<VirtualFile> queue,
            @Nonnull String currentModulePath, @Nonnull String targetModulePath);

    private Module mCurrentModule = null;
    private Module mTargetModule = null;

    @Override
    public final void update(@Nonnull AnActionEvent event) {
        // Disable when no project
        Project project = event.getProject();
        if (project == null) {
            event.getPresentation().setEnabled(false);
            return;
        }

        // Disable when less than 2 modules
        List<Module> moduleList = getModuleList(event);
        if (moduleList.size() < 2) {
            event.getPresentation().setEnabled(false);
            return;
        }

        // Disable when no selected file
        Queue<VirtualFile> selectedFileQueue = getSelectedFileQueue(event);
        if (selectedFileQueue.isEmpty()) {
            event.getPresentation().setEnabled(false);
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
                        event.getPresentation().setEnabled(false);
                        return;
                    }
                }
            }
        }

        event.getPresentation().setEnabled(isEnable(event));
    }

    @Override
    public final void actionPerformed(@Nonnull AnActionEvent event) {
        List<Module> moduleList = getModuleList(event);
        Queue<VirtualFile> selectedFileQueue = getSelectedFileQueue(event);

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
        panel.add(buildComboBoxOfModuleList(event));
        DialogBuilder builder = new DialogBuilder()
                .title(provideDialogTitleText())
                .centerPanel(panel);

        builder.addCancelAction();
        builder.addOkAction().setText(provideDialogActionOkText());
        builder.setOkOperation(() -> {
            builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
            onDialogActionOkInvoked(event, getSelectedFileQueue(event),
                    getModulePath(mCurrentModule), getModulePath(mTargetModule));
        });

        builder.show();
    }

    @Nonnull
    private List<Module> getModuleList(@Nonnull AnActionEvent event) {
        List<Module> list = new ArrayList<>();

        // noinspection ConstantConditions
        Module[] modules = ModuleManager.getInstance(event.getProject()).getModules();
        for (Module module : modules) {
            if (!module.getName().equals(event.getProject().getName())) {
                list.add(module);
            }
        }

        return list;
    }

    @Nonnull
    private ComboBox<String> buildComboBoxOfModuleList(@Nonnull AnActionEvent event) {
        List<Module> moduleList = getModuleList(event);
        moduleList.remove(mCurrentModule);

        List<String> moduleNameList = new ArrayList<>();
        for (Module module : moduleList) {
            moduleNameList.add("Module: '" + module.getName() + "'");
        }

        ComboBox<String> comboBox = new ComboBox<>();
        for (String item : moduleNameList) {
            comboBox.addItem(item);
        }

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
    private Queue<VirtualFile> getSelectedFileQueue(@Nonnull AnActionEvent event) {
        VirtualFile[] files = DataKeys.VIRTUAL_FILE_ARRAY.getData(event.getDataContext());
        return new LinkedList<>(files != null ? Arrays.asList(files) : Collections.emptyList());
    }

    @Nullable
    final PsiFile findPsiFile(@Nonnull AnActionEvent event, @Nonnull VirtualFile file) {
        // noinspection ConstantConditions
        PsiManager manager = PsiManager.getInstance(event.getProject());
        return manager.findFile(file);
    }

    @Nullable
    final PsiDirectory findPsiDirectory(@Nonnull AnActionEvent event, @Nonnull VirtualFile file) {
        // noinspection ConstantConditions
        PsiManager manager = PsiManager.getInstance(event.getProject());
        return manager.findDirectory(file);
    }

    @Nullable
    final PsiDirectory mkdirs(@Nonnull AnActionEvent event, @Nonnull String path) {
        return WriteAction.compute(() -> {
            try {
                // noinspection ConstantConditions
                PsiManager manager = PsiManager.getInstance(event.getProject());
                return DirectoryUtil.mkdirs(manager, path);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    final void invokeLater(@Nonnull AnActionEvent event, @Nonnull Runnable runnable) {
        // noinspection ConstantConditions
        ApplicationManager.getApplication().invokeLater(runnable,
                ModalityState.defaultModalityState(), event.getProject().getDisposed());
    }

    @SuppressWarnings("SameParameterValue")
    final void showErrorHint(@Nonnull AnActionEvent event, @Nonnull String title, @Nonnull String message) {
        // noinspection ConstantConditions
        CommonRefactoringUtil.showErrorHint(event.getProject(), null, title, message, null);
    }
}
