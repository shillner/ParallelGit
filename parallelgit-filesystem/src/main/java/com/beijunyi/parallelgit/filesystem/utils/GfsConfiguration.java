package com.beijunyi.parallelgit.filesystem.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.beijunyi.parallelgit.filesystem.exceptions.HeadAlreadyDefinedException;
import com.beijunyi.parallelgit.utils.RefUtils;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import static com.beijunyi.parallelgit.filesystem.GitFileSystemProvider.*;
import static com.beijunyi.parallelgit.utils.BranchUtils.branchExists;
import static com.beijunyi.parallelgit.utils.CommitUtils.*;
import static com.beijunyi.parallelgit.utils.RepositoryUtils.*;

public class GfsConfiguration {

  private final Repository repo;
  private String branchRef;
  private RevCommit commit;

  public GfsConfiguration(@Nonnull Repository repo) {
    this.repo = repo;
  }

  @Nonnull
  public static GfsConfiguration repo(@Nonnull Repository repo) {
    return new GfsConfiguration(repo);
  }

  @Nonnull
  public static GfsConfiguration inMemoryRepo() {
    return repo(new InMemoryRepository(new DfsRepositoryDescription()));
  }

  @Nonnull
  public static GfsConfiguration fileRepo(@Nonnull File repoDir) throws IOException {
    Repository repo = repoDir.exists() ? openRepository(repoDir) : createRepository(repoDir);
    return repo(repo);
  }

  @Nonnull
  public static GfsConfiguration fileRepo(@Nonnull String repoDir) throws IOException {
    return fileRepo(new File(repoDir));
  }

  @Nonnull
  public static GfsConfiguration fileRepo(@Nonnull Path repoDir) throws IOException {
    return fileRepo(repoDir.toFile());
  }

  @Nonnull
  public static GfsConfiguration fromPath(@Nonnull Path repoDir, @Nonnull Map<String, ?> props) throws IOException {
    return fileRepo(repoDir).readProperties(props);
  }

  @Nonnull
  public static GfsConfiguration fromUri(@Nonnull URI uri, @Nonnull Map<String, ?> props) throws IOException {
    String repoDir = GfsUriUtils.getRepository(uri);
    return fileRepo(repoDir).readProperties(props);
  }

  @Nonnull
  public Repository repository() {
    return repo;
  }

  @Nonnull
  public GfsConfiguration branch(@Nonnull String name) throws IOException {
    if(commit != null)
      throw new HeadAlreadyDefinedException();
    if(!branchExists(name, repo) && commitExists(name, repo))
      commit = getCommit(name, repo);
    else {
      branchRef = RefUtils.ensureBranchRefName(name);
      commit = getCommit(branchRef, repo);
    }
    return this;
  }

  @Nonnull
  public GfsConfiguration branch(@Nonnull Ref ref) throws IOException {
    return branch(ref.getName());
  }

  @Nullable
  public String branch() throws IOException {
    return branchRef;
  }

  @Nonnull
  public GfsConfiguration commit(@Nonnull RevCommit commit) {
    if(branchRef != null || this.commit != null)
      throw new HeadAlreadyDefinedException();
    this.commit = commit;
    return this;
  }

  @Nonnull
  public GfsConfiguration commit(@Nonnull AnyObjectId id) throws IOException {
    return commit(getCommit(id, repo));
  }

  @Nonnull
  public GfsConfiguration commit(@Nonnull String id) throws IOException {
    return commit(getCommit(id, repo));
  }

  @Nullable
  public RevCommit commit() {
    return commit;
  }

  @Nonnull
  private GfsConfiguration readProperties(@Nonnull Map<String, ?> props) throws IOException {
    String branch = (String) props.get(BRANCH);
    if(branch != null)
      branch(branch);
    else {
      String commit = (String) props.get(COMMIT);
      if(commit != null)
        commit(commit);
    }
    return this;
  }

}
