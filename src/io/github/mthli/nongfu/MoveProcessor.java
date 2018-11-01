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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.usageView.UsageInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class MoveProcessor extends MoveFilesOrDirectoriesProcessor {
    private List<PsiReference> mCachedReferenceList = Collections.emptyList();

    @SuppressWarnings("unused")
    MoveProcessor(
            @Nonnull Project project, @Nonnull PsiElement[] elements, @Nonnull PsiDirectory newParent,
            boolean searchInComments, boolean searchInNonJavaFiles,
            @Nullable MoveCallback moveCallback, @Nullable Runnable prepareSuccessfulCallback) {
        super(project, elements, newParent, searchInComments,
                searchInNonJavaFiles, moveCallback, prepareSuccessfulCallback);
    }

    MoveProcessor(
            @Nonnull Project project, @Nonnull PsiElement[] elements, @Nonnull PsiDirectory newParent,
            boolean searchForReferences, boolean searchInComments, boolean searchInNonJavaFiles,
            @Nullable MoveCallback moveCallback, @Nullable Runnable prepareSuccessfulCallback) {
        super(project, elements, newParent, searchForReferences, searchInComments,
                searchInNonJavaFiles, moveCallback, prepareSuccessfulCallback);
    }

    @Override
    @Nonnull
    protected UsageInfo[] findUsages() {
        UsageInfo[] usageInfos = super.findUsages();
        mCachedReferenceList = Arrays.stream(usageInfos)
                .map(UsageInfo::getReference)
                .collect(Collectors.toList());
        return usageInfos;
    }

    @Nonnull
    List<PsiReference> getCachedReferenceList() {
        return mCachedReferenceList;
    }
}
