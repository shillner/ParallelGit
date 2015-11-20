package com.beijunyi.parallelgit.filesystem;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nonnull;

import com.beijunyi.parallelgit.filesystem.requests.CommitRequest;
import com.beijunyi.parallelgit.filesystem.requests.MergeRequest;
import com.beijunyi.parallelgit.filesystem.requests.PersistRequest;
import com.beijunyi.parallelgit.filesystem.utils.GfsBuilder;
import com.beijunyi.parallelgit.filesystem.utils.GfsParams;
import com.beijunyi.parallelgit.filesystem.utils.GitUriUtils;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;

public final class Gfs {

  @Nonnull
  public static GfsBuilder newFileSystem() {
    return new GfsBuilder();
  }

  @Nonnull
  public static GitFileSystem forRevision(@Nonnull String revision, @Nonnull Repository repo) throws IOException {
    return newFileSystem()
             .repository(repo)
             .revision(revision)
             .build();
  }

  @Nonnull
  public static GitFileSystem forRevision(@Nonnull String revision, @Nonnull File repoDir) throws IOException {
    return newFileSystem()
             .repository(repoDir)
             .revision(revision)
             .build();
  }

  @Nonnull
  public static GitFileSystem forRevision(@Nonnull String revision, @Nonnull String repoDir) throws IOException {
    return newFileSystem()
             .repository(repoDir)
             .revision(revision)
             .build();
  }

  @Nonnull
  public static GitFileSystem forCommit(@Nonnull AnyObjectId commit, @Nonnull Repository repo) throws IOException {
    return newFileSystem()
             .repository(repo)
             .commit(commit)
             .build();
  }

  @Nonnull
  public static GitFileSystem fromUri(@Nonnull URI uri, @Nonnull Map<String, ?> properties) throws IOException {
    return newFileSystem()
             .repository(GitUriUtils.getRepository(uri))
             .readParams(GfsParams.getParams(properties))
             .build();
  }

  @Nonnull
  public static GitFileSystem fromPath(@Nonnull Path path, @Nonnull Map<String, ?> properties) throws IOException {
    return newFileSystem()
             .repository(path.toFile())
             .readParams(GfsParams.getParams(properties))
             .build();
  }

  @Nonnull
  public static CommitRequest commit(@Nonnull GitFileSystem gfs) {
    return new CommitRequest(gfs);
  }

  @Nonnull
  public static PersistRequest persist(@Nonnull GitFileSystem gfs) {
    return new PersistRequest(gfs);
  }

  @Nonnull
  public static MergeRequest merge(@Nonnull GitFileSystem gfs) {
    return new MergeRequest(gfs);
  }

}
