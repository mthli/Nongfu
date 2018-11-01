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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;

import javax.annotation.Nonnull;
import java.util.Queue;

public final class MoveToModule extends BaseAction {
    private static final String ERROR_HINT_TITLE = "Move Failed :(";

    @Override
    boolean isEnable(@Nonnull AnActionEvent event) {
        return MoveHandler.canMove(BaseRefactoringAction.getPsiElementArray(event.getDataContext()), null);
    }

    @Override
    @Nonnull
    String provideDialogTitleText() {
        return "Move to Module";
    }

    @Override
    @Nonnull
    String provideDialogActionOkText() {
        return "Move";
    }

    @Override
    void onDialogActionOkInvoked(
            @Nonnull AnActionEvent event, @Nonnull Queue<VirtualFile> queue,
            @Nonnull String currentModulePath, @Nonnull String targetModulePath) {
        VirtualFile currentVirtualFile = queue.poll();
        if (currentVirtualFile == null) {
            return;
        }

        String targetDirectoryPath = currentVirtualFile.getParent().getPath().replace(currentModulePath, targetModulePath);
        PsiDirectory targetPsiDirectory = mkdirs(event, targetDirectoryPath);
        if (targetPsiDirectory == null) {
            showErrorHint(event, ERROR_HINT_TITLE, "targetPsiDirectory null, path: " + currentVirtualFile.getPath());
            onDialogActionOkInvoked(event, queue, currentModulePath, targetModulePath);
            return;
        }

        PsiElement currentPsiElement;
        if (currentVirtualFile.isDirectory()) {
            currentPsiElement = findPsiDirectory(event, currentVirtualFile);
        } else {
            currentPsiElement = findPsiFile(event, currentVirtualFile);
        }

        if (currentPsiElement == null) {
            showErrorHint(event, ERROR_HINT_TITLE, "currentPsiElement null, path: " + currentVirtualFile.getPath());
            onDialogActionOkInvoked(event, queue, currentModulePath, targetModulePath);
            return;
        }

        BaseRefactoringProcessor processor = new MoveFilesOrDirectoriesProcessor(
                event.getProject(), new PsiElement[]{currentPsiElement}, targetPsiDirectory, true, true, true,
                () -> onDialogActionOkInvoked(event, queue, currentModulePath, targetModulePath), () -> {});
        processor.setPrepareSuccessfulSwingThreadCallback(null);
        processor.setPreviewUsages(true);
        processor.run();
    }
}
