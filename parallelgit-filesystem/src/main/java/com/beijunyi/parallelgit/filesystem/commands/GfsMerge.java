package com.beijunyi.parallelgit.filesystem.commands;

import java.io.IOException;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.beijunyi.parallelgit.filesystem.GfsStatusProvider;
import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.exceptions.GfsCheckoutConflictException;
import com.beijunyi.parallelgit.filesystem.exceptions.NoBranchException;
import com.beijunyi.parallelgit.filesystem.io.GfsTreeIterator;
import com.beijunyi.parallelgit.filesystem.merge.MergeConflict;
import com.beijunyi.parallelgit.filesystem.merge.MergeNote;
import com.beijunyi.parallelgit.utils.BranchUtils;
import com.beijunyi.parallelgit.utils.CommitUtils;
import com.beijunyi.parallelgit.utils.RefUtils;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.*;
import org.eclipse.jgit.revwalk.RevCommit;

import static com.beijunyi.parallelgit.filesystem.commands.GfsMerge.Status.*;
import static com.beijunyi.parallelgit.filesystem.io.GfsDefaultCheckout.checkout;
import static com.beijunyi.parallelgit.filesystem.merge.GfsMergeCheckout.handleConflicts;
import static com.beijunyi.parallelgit.filesystem.merge.MergeConflict.readConflicts;
import static com.beijunyi.parallelgit.filesystem.merge.MergeNote.mergeSquash;
import static com.beijunyi.parallelgit.filesystem.utils.GfsPathUtils.toAbsolutePath;
import static com.beijunyi.parallelgit.utils.CommitUtils.listUnmergedCommits;
import static java.util.Collections.singletonList;
import static org.eclipse.jgit.dircache.DirCache.newInCore;
import static org.eclipse.jgit.merge.MergeStrategy.RECURSIVE;

public class GfsMerge extends GfsCommand<GfsMerge.Result> {

  private MergeStrategy strategy = RECURSIVE;
  private MergeFormatter formatter = new MergeFormatter();
  private DirCache cache = newInCore();

  private String branch;
  private Ref branchRef;
  private RevCommit headCommit;
  private String source;
  private Ref sourceRef;
  private RevCommit sourceHeadCommit;
  private boolean fastForwardOnly = false;
  private boolean squash = false;
  private boolean commit = true;
  private PersonIdent committer;
  private String message;

  public GfsMerge(GitFileSystem gfs) {
    super(gfs);
  }

  @Nonnull
  @Override
  protected Result doExecute(GfsStatusProvider.Update update) throws IOException {
    prepareBranchHead();
    prepareSource();
    prepareSourceCommit();
    prepareMessage();

    Result result = null;
    if(isUpToDate())
      result = Result.upToDate(headCommit);
    else if(canBeFastForwarded())
      result = fastForward(update);
    else if(fastForwardOnly)
      result = Result.aborted();

    if(result != null) {
      return result;
    }

    return threeWayMerge(update);
  }

  @Nonnull
  public GfsMerge source(@Nullable String branch) {
    this.source = branch;
    return this;
  }

  @Nonnull
  public GfsMerge source(@Nullable Ref branchRef) {
    this.sourceRef = branchRef;
    return this;
  }

  @Nonnull
  public GfsMerge fastForwardOnly(boolean fastForwardOnly) {
    this.fastForwardOnly = fastForwardOnly;
    return this;
  }

  @Nonnull
  public GfsMerge squash(boolean squash) {
    this.squash = squash;
    return this;
  }

  @Nonnull
  public GfsMerge commit(boolean commit) {
    this.commit = commit;
    return this;
  }

  @Nonnull
  public GfsMerge committer(@Nullable PersonIdent committer) {
    this.committer = committer;
    return this;
  }

  @Nonnull
  public GfsMerge message(@Nullable String message) {
    this.message = message;
    return this;
  }

  @Nonnull
  public GfsMerge strategy(@Nullable MergeStrategy strategy) {
    this.strategy = strategy;
    return this;
  }

  private void prepareBranchHead() throws IOException {
    if(!status.isAttached())
      throw new NoBranchException();
    branch = status.branch();
    branchRef = RefUtils.getBranchRef(branch, repo);

    if(status.isInitialized())
      headCommit = status.commit();
  }

  private void prepareSource() throws IOException {
    if(sourceRef == null) sourceRef = RefUtils.getBranchRef(source, repo);
  }

  private void prepareSourceCommit() throws IOException {
    sourceHeadCommit = CommitUtils.getCommit(sourceRef, repo);
  }

  private void prepareMessage() throws IOException {
    if(message == null) {
      if(squash) {
        List<RevCommit> squashedCommits = listUnmergedCommits(sourceHeadCommit, headCommit, repo);
        message = new SquashMessageFormatter().format(squashedCommits, branchRef);
      } else {
        message = new MergeMessageFormatter().format(singletonList(sourceRef), branchRef);
      }
    }
  }

  private boolean isUpToDate() throws IOException {
    return headCommit != null && CommitUtils.isMergedInto(sourceHeadCommit, headCommit, repo);
  }

  private boolean canBeFastForwarded() throws IOException {
    return headCommit == null || CommitUtils.isMergedInto(headCommit, sourceHeadCommit, repo);
  }

  @Nonnull
  private Result fastForward(GfsStatusProvider.Update update) throws IOException {
    boolean success = tryCheckout(sourceHeadCommit.getTree());
    Result result;
    if(!success) {
      result = Result.checkoutConflict();
    } else if(squash) {
      update.mergeNote(mergeSquash(message));
      result = Result.fastForwardSquashed();
    } else {
      BranchUtils.merge(branch, sourceHeadCommit, sourceRef, FAST_FORWARD.toString(), repo);
      result = Result.fastForward(sourceHeadCommit);
    }
    return result;
  }

  @Nonnull
  private Result threeWayMerge(GfsStatusProvider.Update update) throws IOException {
    Merger merger = prepareMerger();
    boolean success = merger.merge(headCommit, sourceHeadCommit);
    if(success)
      return updateFileSystemStatus(update, merger);
    if(merger instanceof ResolveMerger)
      writeConflicts(update, (ResolveMerger)merger);
    return Result.conflicting();
  }

  @Nonnull
  private Result updateFileSystemStatus(GfsStatusProvider.Update update, Merger merger) throws IOException {
    AnyObjectId treeId = merger.getResultTreeId();
    checkout(gfs, treeId);
    RevCommit newCommit = null;
    if(commit && !squash) {
      prepareCommitter();
      newCommit = CommitUtils.createCommit(message, treeId, committer, committer, Arrays.asList(headCommit, sourceHeadCommit), repo);
      BranchUtils.merge(branch, newCommit, sourceRef, "Merge made by " + strategy.getName() + ".", repo);
    }
    if(!commit) {
      update.mergeNote(MergeNote.mergeNoCommit(sourceHeadCommit, message));
      return Result.mergedNotCommitted();
    }
    if(squash) {
      update.mergeNote(MergeNote.mergeSquash(message));
      return Result.mergedSquashed();
    }
    return Result.merged(newCommit);
  }

  private void writeConflicts(GfsStatusProvider.Update update, ResolveMerger merger) throws IOException {
    Map<String, MergeConflict> conflicts = readConflicts(merger);
    handleConflicts(gfs, conflicts)
      .withFormatter(formatter)
      .checkout(cache);
    message = new MergeMessageFormatter().formatWithConflicts(message, merger.getUnmergedPaths());
    if(squash) {
      update.mergeNote(MergeNote.mergeSquashConflicting(message));
    } else {
      update.mergeNote(MergeNote.mergeConflicting(sourceHeadCommit, message));
    }
  }

  private boolean tryCheckout(AnyObjectId tree) throws IOException {
    try {
      checkout(gfs, tree);
    } catch(GfsCheckoutConflictException e) {
      return false;
    }
    return true;
  }

  @Nonnull
  private Merger prepareMerger() throws IOException {
    Merger merger = strategy.newMerger(repo, true);
    if(merger instanceof ResolveMerger) {
      ResolveMerger resolver = ((ResolveMerger)merger);
      resolver.setDirCache(cache);
      resolver.setCommitNames(new String[] {"BASE", branchRef.getName(), sourceRef.getName()});
      resolver.setWorkingTreeIterator(new GfsTreeIterator(gfs));
    }
    return merger;
  }

  private void prepareCommitter() {
    if(committer == null) committer = new PersonIdent(repo);
  }

  @Nonnull
  private static Map<String, MergeResult<? extends Sequence>> getConflicts(ResolveMerger merger) {
    Map<String, MergeResult<? extends Sequence>> ret = new HashMap<>();
    for(Map.Entry<String, MergeResult<? extends Sequence>> conflict : merger.getMergeResults().entrySet())
      ret.put(toAbsolutePath(conflict.getKey()), conflict.getValue());
    return ret;
  }

  public enum Status {
    CHECKOUT_CONFLICT,
    ABORTED,
    ALREADY_UP_TO_DATE,
    FAST_FORWARD,
    FAST_FORWARD_SQUASHED,
    MERGED,
    MERGED_SQUASHED,
    MERGED_NOT_COMMITTED,
    CONFLICTING
  }

  public static class Result implements GfsCommandResult {

    private final Status status;
    private final RevCommit commit;

    private Result(Status status, @Nullable RevCommit commit) {
      this.status = status;
      this.commit = commit;
    }

    @Nonnull
    public static Result checkoutConflict() {
      return new Result(CHECKOUT_CONFLICT, null);
    }

    @Nonnull
    public static Result aborted() {
      return new Result(ABORTED, null);
    }

    @Nonnull
    public static Result upToDate(RevCommit commit) {
      return new Result(ALREADY_UP_TO_DATE, commit);
    }

    @Nonnull
    public static Result fastForward(RevCommit commit) {
      return new Result(FAST_FORWARD, commit);
    }

    @Nonnull
    public static Result fastForwardSquashed() {
      return new Result(FAST_FORWARD_SQUASHED, null);
    }

    @Nonnull
    public static Result merged(RevCommit commit) {
      return new Result(MERGED, commit);
    }

    @Nonnull
    public static Result mergedSquashed() {
      return new Result(MERGED_SQUASHED, null);
    }

    @Nonnull
    public static Result mergedNotCommitted() {
      return new Result(MERGED_NOT_COMMITTED, null);
    }

    @Nonnull
    public static Result conflicting() {
      return new Result(CONFLICTING, null);
    }

    @Override
    public boolean isSuccessful() {
      switch(status) {
        case ALREADY_UP_TO_DATE:
        case FAST_FORWARD:
        case FAST_FORWARD_SQUASHED:
        case MERGED:
        case MERGED_SQUASHED:
        case MERGED_NOT_COMMITTED:
          return true;
        default:
          return false;
      }
    }

    @Nonnull
    public Status getStatus() {
      return status;
    }

    @Nullable
    public RevCommit getCommit() {
      return commit;
    }

  }

}