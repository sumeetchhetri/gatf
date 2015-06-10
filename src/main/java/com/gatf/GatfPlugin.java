package com.gatf;

import java.util.List;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

public interface GatfPlugin {
	void doExecute(GatfPluginConfig configuration, List<String> files) throws MojoFailureException;
	void setProject(MavenProject project);
	void shutdown();
}
