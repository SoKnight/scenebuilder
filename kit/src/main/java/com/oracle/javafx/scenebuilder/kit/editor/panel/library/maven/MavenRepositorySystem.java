/*
 * Copyright (c) 2016, 2024, Gluon and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation and Gluon nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.javafx.scenebuilder.kit.editor.panel.library.maven;

import com.oracle.javafx.scenebuilder.kit.editor.panel.library.maven.preset.MavenPresets;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.maven.repository.Repository;
import com.oracle.javafx.scenebuilder.kit.preferences.RepositoryPreferences;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Slf4j
public class MavenRepositorySystem {

    // TODO: Manage List of Repositories
    // TODO: Manage private repositories and credentials
    
    private RepositorySystem system;

    private RepositorySystemSession session;

    private LocalRepository localRepo;
    
    private VersionRangeResult rangeResult;
    
    private final boolean onlyReleases;
    private final String userM2Repository;
    private final RepositoryPreferences repositoryPreferences;
    
    public MavenRepositorySystem(boolean onlyReleases, String userM2Repository,
                                 RepositoryPreferences repositoryPreferences) {
        this.onlyReleases = onlyReleases;
        this.userM2Repository = userM2Repository;
        this.repositoryPreferences = repositoryPreferences;
        initRepositorySystem();
    }
    
    private void initRepositorySystem() {
        system = new RepositorySystemSupplier().get();
        if (system == null) {
            throw new RuntimeException("Error initializing repository system");
        }
        localRepo = new LocalRepository(Path.of(userM2Repository));

        // Exclude test and provided dependencies
        DependencySelector dependencySelector = new AndDependencySelector(
            new ScopeDependencySelector("test", "provided"),
            new OptionalDependencySelector(), new ExclusionDependencySelector()
        );
        session = system.createSessionBuilder()
            .withTransferListener(new AbstractTransferListener() {
                @Override
                public void transferSucceeded(TransferEvent event) {
                    log.debug("Transfer succeeded: {}", event);
                }
                @Override
                public void transferFailed(TransferEvent event) {
                    log.debug("Transfer failed: {}", event);
                }
            })
            .withRepositoryListener(new AbstractRepositoryListener() {
                @Override
                public void artifactResolved(RepositoryEvent event) {
                    log.debug("Artifact resolved: {}", event);
                }
            })
            .withLocalRepositories(localRepo)
            .setDependencySelector(dependencySelector)
            .build();
    }

    public List<RemoteRepository> getRepositories() {
        final List<RemoteRepository> list = MavenPresets.getPresetRepositories().stream()
                .filter(r -> !onlyReleases || !r.getId().toUpperCase(Locale.ROOT).contains("SNAPSHOT"))
                .map(this::createRepository)
                .collect(Collectors.toList());
        list.addAll(repositoryPreferences.getRepositories().stream()
                .filter(r -> !onlyReleases || !r.getId().toUpperCase(Locale.ROOT).contains("SNAPSHOT"))
                .map(this::createRepository)
                .toList());
        return list;
    }
    
    public RemoteRepository getRemoteRepository(Version version) {
        if (rangeResult == null || version == null) {
            return null;
        }
        return getRepositories()
                .stream()
                .filter(r -> r.getId().equals(rangeResult.getRepository(version).getId()))
                .findFirst()
                .orElse(new RemoteRepository
                        .Builder(MavenPresets.LOCAL, "default", session.getLocalRepository().getBasePath().toAbsolutePath().toString())
                        .build());
    }
    
    public List<Version> findVersions(Artifact artifact) {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(artifact);
        rangeRequest.setRepositories(getRepositories());
        try {
            rangeResult = system.resolveVersionRange(session, rangeRequest);
            cleanMetadata(artifact);
            
            return rangeResult.getVersions();
        } catch (VersionRangeResolutionException ex) {
            log.debug("VersionRangeResolutionException finding version for artifact {}", artifact, ex);
        }
        return new ArrayList<>();
    }
    
    public Version findLatestVersion(Artifact artifact) {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(artifact);
        rangeRequest.setRepositories(getRepositories());
        try {
            rangeResult = system.resolveVersionRange(session, rangeRequest);
            cleanMetadata(artifact);
            return rangeResult.getVersions().stream()
                .filter(v -> !v.toString().toLowerCase(Locale.ROOT).contains("snapshot"))
                .max(Comparator.naturalOrder())
                .orElse(null);
        } catch (VersionRangeResolutionException ex) {
            log.debug("VersionRangeResolutionException finding latest version for artifact {}", artifact, ex);
        }
        return null;
    }
    
    private void cleanMetadata(Artifact artifact) {
        final Path path = localRepo.getBasePath()
            .resolve(artifact.getGroupId().replaceAll("\\.", Matcher.quoteReplacement(File.separator)))
            .resolve(artifact.getArtifactId());
        final DefaultMetadata metadata = new DefaultMetadata("maven-metadata.xml", Metadata.Nature.RELEASE);
        getRepositories()
            .stream()
            .map(r -> session.getLocalRepositoryManager().getPathForRemoteMetadata(metadata, r, ""))
            .forEach(s -> {
                Path file = path.resolve(s);
                if (Files.exists(file)) {
                    try {
                        Files.delete(file);
                        Files.delete(new File(file + ".sha1").toPath());
                    } catch (IOException ex) {
                        log.debug("Error deleting file '{}'", file, ex);
                    }
                }
            });
    }
        
    public String resolveArtifacts(RemoteRepository remoteRepository, Artifact... artifact) {

        List<Artifact> artifacts = Stream.of(artifact)
                .map(a -> {
                    ArtifactRequest artifactRequest = new ArtifactRequest();
                    artifactRequest.setArtifact(a);
                    artifactRequest.setRepositories(remoteRepository == null ? getRepositories() : List.of(remoteRepository));
                    return artifactRequest;
                })
                .map(ar -> {
                    try {
                        ArtifactResult result = system.resolveArtifact(session, ar);
                        return result.getArtifact();
                    } catch (ArtifactResolutionException ex) {
                        log.debug("ArtifactResolutionException for artifact request {}", ar, ex);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        List<Path> sha1Paths = null;
        if (!artifacts.isEmpty()) {
            sha1Paths = artifacts.stream()
                .filter(a -> a.getPath() != null)
                .map(a -> Path.of(a.getPath().toAbsolutePath() + ".sha1"))
                .toList();

            InstallRequest installRequest = new InstallRequest();
            installRequest.setArtifacts(artifacts);
            try {
                system.install(session, installRequest);
            } catch (InstallationException ex) {
                log.debug("InstallationException for install request {}", installRequest, ex);
            }
        } 

        // return path from local m2
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact[0]);
        String absolutePath = "";
        try {
            final Path jarFile = system.resolveArtifact(session, artifactRequest).getArtifact().getPath();
            absolutePath = jarFile.toAbsolutePath().toString();
            if (sha1Paths != null) {
                sha1Paths.forEach(path -> copyFile(path, jarFile.getParent().resolve(path.getFileName())));
            }
        } catch (ArtifactResolutionException ex) {
            log.debug("ArtifactResolutionException for artifact request {}", artifactRequest, ex);
        }

        return absolutePath;
    }
        
    public String resolveDependencies(RemoteRepository remoteRepository, Artifact artifact) {
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifact, "compile"));
        collectRequest.setRepositories(remoteRepository == null ? getRepositories() : List.of(remoteRepository));

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);

        try {
            DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
            List<ArtifactResult> artifactResults = dependencyResult.getArtifactResults();

            return artifactResults.stream()
                    .skip(1) // exclude jar itself
                    .map(a -> a.getArtifact().getPath().toAbsolutePath().toString())
                    .collect(Collectors.joining(File.pathSeparator));
        } catch (DependencyResolutionException ex) {
            log.debug("DependencyResolutionException for artifact {}", artifact, ex);
        }
        return "";
    }
    
    private RemoteRepository createRepository(Repository repository) {
        Authentication auth = null;
        if (repository.getUser() != null && !repository.getUser().isEmpty() && 
            repository.getPassword() != null && !repository.getPassword().isEmpty()) {
            auth = new AuthenticationBuilder()
                    .addUsername(repository.getUser())
                    .addPassword(repository.getPassword())
                    .build();
        }

        return new RemoteRepository
                .Builder(repository.getId() , repository.getType(), repository.getURL())
                .setSnapshotPolicy(onlyReleases ? new RepositoryPolicy(false, null, null) : new RepositoryPolicy())
                .setAuthentication(auth)
                .build();
    }
    
    public String validateRepository(Repository repository) {
        RemoteRepository remoteRepository = createRepository(repository);
        
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(new DefaultArtifact("test:test:1.0"));
        artifactRequest.setRepositories(List.of(remoteRepository));;
        try {
            system.resolveArtifact(session, artifactRequest);
        } catch (ArtifactResolutionException ex) {
            final String rootCauseMessage = getExceptionCause(ex).toString();
            if (rootCauseMessage != null && !rootCauseMessage.contains("ArtifactNotFoundException")) {
                return rootCauseMessage;
            }
        }
        
        return "";
    }

    private static void copyFile(Path source, Path destination)  {
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, REPLACE_EXISTING);
        } catch (IOException ex) {
            log.debug("Error copying file '{}' to destination '{}'", source, destination, ex);
        }
    }

    private static Throwable getExceptionCause(Throwable e) {
        Throwable t = e;
        Throwable cause;
        while (null != (cause = t.getCause()) && t != cause) {
            t = cause;
        }
        return t;
    }

}
