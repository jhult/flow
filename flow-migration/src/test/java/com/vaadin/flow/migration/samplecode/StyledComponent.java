/*
 * Copyright 2000-2020 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.migration.samplecode;

import java.io.Serializable;
import java.util.List;

import com.vaadin.flow.component.dependency.StyleSheet;

@StyleSheet("frontend://styles/foo.css")
@StyleSheet("base://styles/foo1.css")
@StyleSheet("context://styles/foo2.css")
@StyleSheet("styles/bar.css")
@StyleSheet("/styles/bar1.css")
@StyleSheet("styles/src/baz.css")
public class StyledComponent<T extends List<?> & Serializable>
        extends GenericComponent<T, String> {

}
