package com.google.eclipse.protobuf.ui.scoping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

import com.google.eclipse.protobuf.ui.preferences.paths.DirectoryPath;

public final class MavenResolverHelper {

	public static List<DirectoryPath> resolveMavenDependencies(IProject aProject) throws CoreException {
		if (aProject.isOpen() && aProject.hasNature(IMavenConstants.NATURE_ID)) {
			final IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getProject(aProject);	
			
			final IMaven maven =  MavenPlugin.getMaven();
			final IMavenExecutionContext context = maven.createExecutionContext();
			context.execute((ctx, mon) -> {
				final ProjectBuildingRequest configuration = ctx.newProjectBuildingRequest();
			    configuration.setProject(facade.getMavenProject());
			    configuration.setResolveDependencies(true);
			    return MavenPlugin.getMaven().readMavenProject(facade.getPomFile(), configuration);
			}, null);
			
			final MavenProject mavenProject = facade.getMavenProject(null);

			ArrayList<DirectoryPath> paths = new ArrayList<>();
			for (Artifact artifact : mavenProject.getArtifacts()) {
				if (artifact.getFile() != null) {
					paths.add(DirectoryPath.parse(artifact.getFile().getAbsolutePath(), aProject));
					artifact.getFile();
				}
			}

			return paths;
		}
		
		return Collections.emptyList();
	}

}
