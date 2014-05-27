/*
 * Copyright 2013-2014 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.urswolfer.gerrit.client.rest.http.common;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.thoughtworks.xstream.XStream;
import org.testng.Assert;

/**
 * @author Thomas Forrer
 */
public class GerritAssert {

    /**
     * Assert that two {@link com.google.gerrit.extensions.common.ChangeInfo}s are equal, i.e. all their properties
     * are equal.
     *
     * Note: This is currently achieved by streaming both objects in an XML output, since the
     * {@link com.google.gerrit.extensions.common.ChangeInfo} class does not implement <code>equals()</code> and
     * <code>hashCode()</code> methods.
     */
    public static void assertEquals(ChangeInfo actual, ChangeInfo expected) {
        assertXmlOutputEqual(actual, expected);
    }

    public static void assertEquals(ProjectInfo actual, ProjectInfo expected) {
        assertXmlOutputEqual(actual, expected);
    }

    public static void assertEquals(AccountInfo actual, AccountInfo expected) {
        assertXmlOutputEqual(actual, expected);
    }

    private static void assertXmlOutputEqual(Object actual, Object expected) {
        XStream xStream = new XStream();
        xStream.setMode(XStream.NO_REFERENCES);
        String actualXml = xStream.toXML(actual);
        String expectedXml = xStream.toXML(expected);

        Assert.assertEquals(actualXml, expectedXml);
    }
}
