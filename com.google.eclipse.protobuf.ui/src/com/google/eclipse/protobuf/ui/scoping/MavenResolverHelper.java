package com.google.eclipse.protobuf.ui.scoping;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

import com.google.eclipse.protobuf.ui.preferences.paths.DirectoryPath;

public final class MavenResolverHelper {

	public static List<DirectoryPath> resolveMavenDependencies(IProject aProject) throws CoreException {
		if (aProject.isOpen() && aProject.hasNature(IMavenConstants.NATURE_ID)) {
			final IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getProject(aProject);

			final IMavenExecutionContext context;
			
			try {
				Class<?> toolboxClass = MavenResolverHelper.class.getClassLoader()
					.loadClass("org.eclipse.m2e.core.internal.IMavenToolbox");
				
				// If the class can be loaded, m2e 2.x is installed. We compile against 1.x (because we compile
				// against Neon, which is the minimum necessary for this plugin) so we must implement the slight
				// breaking changes in the dependency resolution via reflection.
				try {
					context = (IMavenExecutionContext) facade.getClass().getMethod("createExecutionContext", new Class[0])
							.invoke(facade, new Object[0]);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
						| NoSuchMethodException | SecurityException e) {
					throw new RuntimeException(e);
				}
				context.execute((ctx, mon) -> {
					ProjectBuildingRequest configuration = ctx.newProjectBuildingRequest();
					configuration.setProject(facade.getMavenProject());
					configuration.setResolveDependencies(true);
					try {
						Object toolbox = toolboxClass.getMethod("of", new Class[] { IMavenExecutionContext.class })
								.invoke(null, new Object[] { ctx });
						return toolboxClass
								.getMethod("readMavenProject",
										new Class[] { java.io.File.class, ProjectBuildingRequest.class })
								.invoke(toolbox, new Object[] { facade.getPomFile(), configuration });
					} catch (IllegalAccessException | IllegalArgumentException
							| InvocationTargetException | NoSuchMethodException | SecurityException exc) {
						throw new RuntimeException(exc);
					}
				}, null);
				
			} catch(ClassNotFoundException exc) {
				// Expected in case of m2e 1.x which does not yet have the IMavenToolbox.
				// We can directly invoke it here.
				ProjectBuildingRequest configuration = MavenPlugin.getMaven().getExecutionContext().newProjectBuildingRequest();
			    configuration.setProject(facade.getMavenProject());
			    configuration.setResolveDependencies(true);
			    MavenPlugin.getMaven().readMavenProject(facade.getPomFile(), configuration);
			}

			ArrayList<DirectoryPath> paths = new ArrayList<>();
			for (Artifact artifact : facade.getMavenProject().getArtifacts()) {
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
