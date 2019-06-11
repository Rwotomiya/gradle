/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.fingerprint.impl;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.impl.FileSystemSnapshotBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileHasher hasher;
    private final StringInterner stringInterner;
    private final FileSystemSnapshotter fileSystemSnapshotter;

    public DefaultFileCollectionSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystemSnapshotter fileSystemSnapshotter) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
    }

    @Override
    public List<FileSystemSnapshot> snapshot(FileCollection fileCollection) {
        FileCollectionLeafVisitorImpl visitor = new FileCollectionLeafVisitorImpl();
        ((FileCollectionInternal) fileCollection).visitLeafCollections(visitor);
        return visitor.getRoots();
    }


    private class FileCollectionLeafVisitorImpl implements FileCollectionLeafVisitor {
        private final List<FileSystemSnapshot> roots = new ArrayList<FileSystemSnapshot>();

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                roots.add(fileSystemSnapshotter.snapshot(file));
            }
        }

        @Override
        public void visitGenericFileTree(FileTreeInternal fileTree) {
            roots.add(snapshotFileTree(fileTree));
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns) {
            roots.add(fileSystemSnapshotter.snapshotDirectoryTree(root, patterns));
        }

        public List<FileSystemSnapshot> getRoots() {
            return roots;
        }
    }

    private FileSystemSnapshot snapshotFileTree(final FileTreeInternal tree) {
        final FileSystemSnapshotBuilder builder = new FileSystemSnapshotBuilder(stringInterner);
        tree.visitTreeOrBackingFile(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                builder.addDir(dirDetails.getFile(), dirDetails.getRelativePath().getSegments());
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                builder.addFile(fileDetails.getFile(), fileDetails.getRelativePath().getSegments(), regularFileSnapshot(fileDetails));
            }

            private RegularFileSnapshot regularFileSnapshot(FileVisitDetails fileDetails) {
                return new RegularFileSnapshot(stringInterner.intern(fileDetails.getFile().getAbsolutePath()), fileDetails.getName(), hasher.hash(fileDetails), fileDetails.getLastModified());
            }
        });
        return builder.build();
    }
}
