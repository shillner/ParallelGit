package com.beijunyi.parallelgit.filesystem.io;

import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;

public interface GitFileAttributeView {

  @Nullable
  AnyObjectId getObjectId() throws IOException;

  @Nonnull
  FileMode getFileMode();

}
