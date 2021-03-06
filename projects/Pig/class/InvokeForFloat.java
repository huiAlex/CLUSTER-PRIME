/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pig.builtin;

import org.apache.pig.impl.logicalLayer.FrontendException;

/**
 * @see GenericInvoker
 */

public class InvokeForFloat extends GenericInvoker<Float> {

     public InvokeForFloat() {}

     public InvokeForFloat(String fullName) throws FrontendException, SecurityException, ClassNotFoundException, NoSuchMethodException {
       super(fullName);
     }
     
     public InvokeForFloat(String fullName, String paramSpecsStr) throws FrontendException, SecurityException, ClassNotFoundException, NoSuchMethodException {
         super(fullName, paramSpecsStr);
     }

     public InvokeForFloat(String fullName, String paramSpecsStr, String isStatic)
     throws ClassNotFoundException, FrontendException, SecurityException, NoSuchMethodException {
         super(fullName, paramSpecsStr, isStatic);
     }
 }
