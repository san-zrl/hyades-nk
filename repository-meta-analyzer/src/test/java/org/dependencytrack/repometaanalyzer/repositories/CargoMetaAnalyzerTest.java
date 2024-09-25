/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */

package org.dependencytrack.repometaanalyzer.repositories;

import com.github.packageurl.PackageURL;

import org.apache.http.impl.client.HttpClients;
import org.dependencytrack.persistence.model.Component;
import org.dependencytrack.persistence.model.RepositoryType;
import org.dependencytrack.repometaanalyzer.model.MetaModel;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

public class CargoMetaAnalyzerTest {

    private IMetaAnalyzer analyzer;

    @BeforeEach
    void beforeEach() {
        analyzer = new CargoMetaAnalyzer();
        analyzer.setHttpClient(HttpClients.createDefault());
    }

    @Test
    public void testAnalyzerX() throws Exception {
        Component component = new Component();
        component.setPurl(new PackageURL("pkg:cargo/rand@0.7.2"));

        Assert.assertTrue(analyzer.isApplicable(component));
        Assert.assertEquals(RepositoryType.CARGO, analyzer.supportedRepositoryType());
        MetaModel metaModel = analyzer.analyze(component);
        Assert.assertNotNull(metaModel.getLatestVersion());
        Assert.assertNotNull(metaModel.getPublishedTimestamp());
    }    
}
