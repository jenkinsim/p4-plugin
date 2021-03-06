package org.jenkinsci.plugins.p4.tagging;

import java.io.IOException;

import org.jenkinsci.plugins.p4.workspace.Expand;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

public class TagNotifierStep extends TagNotifier implements SimpleBuildStep {

	public TagNotifierStep(String rawLabelName, String rawLabelDesc, boolean onlyOnSuccess) {
		super(rawLabelName, rawLabelDesc, onlyOnSuccess);
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {

		// return early if label not required
		if (onlyOnSuccess && run.getResult() != Result.SUCCESS) {
			return;
		}
		try {
			// Expand label name and description
			EnvVars env = run.getEnvironment(listener);
			Expand expand = new Expand(env);
			String name = expand.format(rawLabelName, false);
			String description = expand.format(rawLabelDesc, false);

			// Get TagAction and check for promoted builds
			TagAction tagAction = getTagAction(env, run);

			// Label with TagAction
			tagAction.labelBuild(listener, name, description, workspace);
		} catch (Exception e) {
			final String err = "P4: Could not label: " + e;
			log(err);
			throw new AbortException(err);
		}
	}

	private TagAction getTagAction(EnvVars env, Run<?, ?> run) {
		TagAction tagAction = (TagAction) run.getAction(TagAction.class);

		// process promoted builds?
		if (tagAction == null) {
			String jobName = env.get("PROMOTED_JOB_NAME");
			if (jobName == null || jobName.isEmpty()) {
				log("No tag information; not a promotion job.");
				return tagAction;
			}

			String buildNumber = env.get("PROMOTED_NUMBER");
			if (buildNumber == null || buildNumber.isEmpty()) {
				log("No tag information; not a promotion job.");
				return tagAction;
			}

			Jenkins j = Jenkins.getInstance();
			if (j == null) {
				log("Jenkins instance is null!");
				return tagAction;
			}
			
			Job<?, ?> job = j.getItemByFullName(jobName, Job.class);
			if (job == null) {
				log("No job information; is it a valid Perforce job?");
				return tagAction;
			}

			int buildNum = Integer.parseInt(buildNumber);
			run = job.getBuildByNumber(buildNum);
			if (run == null) {
				log("No build number; is it a valid Perforce job?");
				return tagAction;
			}
			
			tagAction = run.getAction(TagAction.class);

			if (tagAction == null) {
				log("No tag information; is it a valid Perforce job?");
				return tagAction;
			}
		}
		return tagAction;
	}
}
