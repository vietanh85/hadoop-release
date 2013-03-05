/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.namenode.snapshot;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.hdfs.protocol.NSQuotaExceededException;
import org.apache.hadoop.hdfs.server.namenode.DatanodeDescriptor;
import org.apache.hadoop.hdfs.server.namenode.INodeFile;
import org.apache.hadoop.hdfs.server.namenode.INodeFileUnderConstruction;

/**
 * Represent an {@link INodeFileUnderConstruction} that is snapshotted.
 * Note that snapshot files are represented by
 * {@link INodeFileUnderConstructionSnapshot}.
 */
@InterfaceAudience.Private
public class INodeFileUnderConstructionWithSnapshot
    extends INodeFileUnderConstruction implements FileWithSnapshot {
  /**
   * Factory for {@link INodeFileUnderConstruction} diff.
   */
  static class FileUcDiffFactory extends FileDiffFactory {
    static final FileUcDiffFactory INSTANCE = new FileUcDiffFactory();

    @Override
    INodeFileUnderConstruction createSnapshotCopy(INodeFile file) {
      final INodeFileUnderConstruction uc = (INodeFileUnderConstruction)file;
      final INodeFileUnderConstruction copy = new INodeFileUnderConstruction(
          uc, uc.getClientName(), uc.getClientMachine(), uc.getClientNode());
      copy.setBlocks(null);
      return copy;
    }
  }

  private final FileDiffList diffs;
  private boolean isCurrentFileDeleted = false;

  INodeFileUnderConstructionWithSnapshot(final INodeFile f,
      final String clientName,
      final String clientMachine,
      final DatanodeDescriptor clientNode,
      final FileDiffList diffs) {
    super(f, clientName, clientMachine, clientNode);
    this.diffs = diffs != null? diffs: new FileDiffList();
    this.diffs.setFactory(FileUcDiffFactory.INSTANCE);
  }

  /**
   * Construct an {@link INodeFileUnderConstructionWithSnapshot} based on an
   * {@link INodeFileUnderConstruction}.
   * 
   * @param f The given {@link INodeFileUnderConstruction} instance
   */
  public INodeFileUnderConstructionWithSnapshot(INodeFileUnderConstruction f,
      final FileDiffList diffs) {
    this(f, f.getClientName(), f.getClientMachine(), f.getClientNode(), diffs);
  }
  
  @Override
  protected INodeFileWithSnapshot toINodeFile(final long mtime) {
    final long atime = getModificationTime();
    final INodeFileWithSnapshot f = new INodeFileWithSnapshot(this, getDiffs());
    f.setModificationTime(mtime);
    f.setAccessTime(atime);
    return f;
  }

  @Override
  public boolean isCurrentFileDeleted() {
    return isCurrentFileDeleted;
  }

  @Override
  public INodeFile getSnapshotINode(Snapshot snapshot) {
    return diffs.getSnapshotINode(snapshot, this);
  }

  @Override
  public INodeFileUnderConstructionWithSnapshot recordModification(
      final Snapshot latest) throws NSQuotaExceededException {
    if (isInLatestSnapshot(latest)) {
      diffs.saveSelf2Snapshot(latest, this, null);
    }
    return this;
  }

  @Override
  public INodeFile asINodeFile() {
    return this;
  }
  
  @Override
  public FileDiffList getDiffs() {
    return diffs;
  }

  @Override
  public int cleanSubtree(final Snapshot snapshot, Snapshot prior,
      final BlocksMapUpdateInfo collectedBlocks)
          throws NSQuotaExceededException {
    if (snapshot == null) { // delete the current file
      recordModification(prior);
      isCurrentFileDeleted = true;
      Util.collectBlocksAndClear(this, collectedBlocks);
    } else { // delete a snapshot
      return diffs.deleteSnapshotDiff(snapshot, prior, this, collectedBlocks);
    }
    return 1;
  }

  @Override
  public String toDetailString() {
    return super.toDetailString()
        + (isCurrentFileDeleted()? " (DELETED), ": ", ") + diffs;
  }
}
