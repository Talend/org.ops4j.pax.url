package org.apache.maven.repository;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * An artifact that is linked to another artifact. In particular, a sub artifact has no own version but uses the version
 * of the associated main artifact.
 * 
 * @author Benjamin Bentmann
 */
public interface SubArtifact
    extends Artifact
{

    /**
     * Gets the artifact this artifact is linked to.
     * 
     * @return The main artifact, never {@code null}.
     */
    Artifact getMainArtifact();

    /**
     * Does nothing, the sub artifact will always use the version of its main artifact.
     */
    void setVersion( String version );

    /**
     * Creates a deep copy of this artifact.
     * 
     * @return The clone of this artifact, never {@code null}.
     */
    SubArtifact clone();

}
