package com.github.ferstl.depgraph;

import java.util.Collection;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import com.github.ferstl.depgraph.dot.AttributeBuilder;
import com.github.ferstl.depgraph.dot.EdgeRenderer;
import com.github.ferstl.depgraph.dot.GraphBuilder;
import com.github.ferstl.depgraph.dot.Node;


class AggregatingDotGraphFactory implements GraphFactory {

  private final DependencyGraphBuilder dependencyGraphBuilder;
  private final ArtifactFilter artifactFilter;
  private final GraphBuilder graphBuilder;

  public AggregatingDotGraphFactory(
      DependencyGraphBuilder dependencyGraphBuilder, ArtifactFilter artifactFilter, GraphBuilder graphBuilder) {

    this.dependencyGraphBuilder = dependencyGraphBuilder;
    this.artifactFilter = artifactFilter;
    this.graphBuilder = graphBuilder;
  }

  @Override
  public String createGraph(MavenProject parent) throws DependencyGraphException {
    @SuppressWarnings("unchecked")
    List<MavenProject> collectedProjects = parent.getCollectedProjects();
    buildModuleTree(parent, this.artifactFilter, this.graphBuilder);

    for (MavenProject collectedProject : collectedProjects) {
      // Process project only if its artifact is not filtered
      if (this.artifactFilter.include(collectedProject.getArtifact())) {
        DependencyNode root;
        try {
          root = this.dependencyGraphBuilder.buildDependencyGraph(collectedProject, this.artifactFilter);
        } catch (DependencyGraphBuilderException e) {
          throw new DependencyGraphException(e);
        }

        DotBuildingVisitor visitor = new DotBuildingVisitor(this.graphBuilder);
        root.accept(visitor);
      }
    }

    return this.graphBuilder.toString();
  }

  private void buildModuleTree(MavenProject parentProject, ArtifactFilter filter, GraphBuilder graphBuilder) {
    @SuppressWarnings("unchecked")
    Collection<MavenProject> collectedProjects = parentProject.getCollectedProjects();
    for (MavenProject collectedProject : collectedProjects) {
      MavenProject child = collectedProject;
      MavenProject parent = collectedProject.getParent();

      while (parent != null) {
        Node parentNode = filterProject(parent, filter);
        Node childNode = filterProject(child, filter);

        graphBuilder.addEdge(parentNode, childNode, DottedEdgeRenderer.DOTTED);

        // Stop if we reached this project!
        if (parent.equals(parentProject)) {
          break;
        }

        child = parent;
        parent = parent.getParent();
      }
    }
  }

  private Node filterProject(MavenProject project, ArtifactFilter filter) {
    Artifact artifact = project.getArtifact();
    if (filter.include(artifact)) {
      return new DependencyNodeAdapter(artifact);
    }

    return null;
  }

  enum DottedEdgeRenderer implements EdgeRenderer {
    DOTTED {

      @Override
      public String createEdgeAttributes(Node from, Node to) {
        return new AttributeBuilder().style("dotted").toString();
      }

    }
  }
}
