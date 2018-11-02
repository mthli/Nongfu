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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.move.MoveHandler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

public final class MoveToModule extends BaseAction {
    private static final String ERROR_HINT_TITLE = "Move Failed :(";

    private MoveProcessor mMoveProcessor;
    private List<PsiReference> mCachedReferenceList = Collections.emptyList();

    @Override
    boolean isEnable(@Nonnull AnActionEvent event) {
        // If every single PsiElement can move, then return true
        PsiElement[] array = BaseRefactoringAction.getPsiElementArray(event.getDataContext());
        for (PsiElement element : array) {
            if (!MoveHandler.canMove(new PsiElement[]{element}, null)) {
                return false;
            }
        }

        return true;
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
            @Nonnull Project project, @Nonnull Queue<VirtualFile> queue,
            @Nonnull String currentModulePath, @Nonnull String targetModulePath) {
        VirtualFile currentVirtualFile = queue.poll();
        if (currentVirtualFile == null) {
            return;
        }

        String targetDirectoryPath = currentVirtualFile.getParent().getPath().replace(currentModulePath, targetModulePath);
        PsiDirectory targetPsiDirectory = mkdirs(project, targetDirectoryPath);
        if (targetPsiDirectory == null) {
            showErrorHint(project, ERROR_HINT_TITLE, "targetPsiDirectory null, path: " + currentVirtualFile.getPath());
            onDialogActionOkInvoked(project, queue, currentModulePath, targetModulePath);
            return;
        }

        PsiElement currentPsiElement;
        if (currentVirtualFile.isDirectory()) {
            currentPsiElement = findPsiDirectory(project, currentVirtualFile);
        } else {
            currentPsiElement = findPsiFile(project, currentVirtualFile);
        }

        if (currentPsiElement == null) {
            showErrorHint(project, ERROR_HINT_TITLE, "currentPsiElement null, path: " + currentVirtualFile.getPath());
            onDialogActionOkInvoked(project, queue, currentModulePath, targetModulePath);
            return;
        }

        // Little trick, avoid redundant check
        boolean shouldCheckReferences;
        if (mCachedReferenceList.isEmpty()) {
            shouldCheckReferences = true;
        } else {
            boolean matchAllCacheReferences = true;
            for (PsiReference reference : mCachedReferenceList) {
                if (!reference.isReferenceTo(currentPsiElement)) {
                    matchAllCacheReferences = false;
                }
            }

            shouldCheckReferences = !matchAllCacheReferences;
        }

        mMoveProcessor = new MoveProcessor(
                project, new PsiElement[]{currentPsiElement}, targetPsiDirectory,
                shouldCheckReferences, shouldCheckReferences, shouldCheckReferences,
                () -> {
                    if (shouldCheckReferences && mMoveProcessor != null) {
                        mCachedReferenceList = mMoveProcessor.getCachedReferenceList();
                    }

                    invokeLater(project, () -> onDialogActionOkInvoked(project, queue, currentModulePath, targetModulePath));
                }, () -> {});
        mMoveProcessor.setPrepareSuccessfulSwingThreadCallback(null);
        mMoveProcessor.setPreviewUsages(shouldCheckReferences && currentVirtualFile.isWritable());
        mMoveProcessor.run();
    }
}
