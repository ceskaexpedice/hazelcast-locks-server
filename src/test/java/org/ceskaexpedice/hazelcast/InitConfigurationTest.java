/*
 * Copyright (C) 2025  Inovatika
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ceskaexpedice.hazelcast;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class InitConfigurationTest {

    @Test
    public void testInitConfig() throws ParserConfigurationException, IOException, SAXException {
        String path = HazelcastServerNodeStarter.initDefaultConfiguration();
        assertNotNull(path);

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document parsed = builder.parse(new File(path));

        Element rootElm = parsed.getDocumentElement();
        assertEquals(rootElm.getNodeName(), "hazelcast");

    }

}
