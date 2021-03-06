/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.standardout.gradle.plugin.platform.internal.util

import java.util.regex.Matcher

import org.gradle.api.Project
import org.osgi.framework.Version
import org.standardout.gradle.plugin.platform.internal.BundleArtifact
import org.standardout.gradle.plugin.platform.internal.Feature
import org.standardout.gradle.plugin.platform.internal.config.BndConfig

import aQute.bnd.osgi.Analyzer


class VersionUtil {
	
	/**
	 * Convert a version string to a valid OSGi version.
	 * 
	 * @param version the version string
	 * @param onInvalid closure that is called with the OSGi version if the
	 *   original version string yielded no valid OSGi version
	 * @return the OSGi version, the string representation may differ from
	 *   the original version string
	 */
	static Version toOsgiVersion(String version, Closure onInvalid = null) {
		Version osgiVersion
		try {
			osgiVersion = Version.parseVersion(version)
		} catch (NumberFormatException e) {
			// try again with adapted versions
			Matcher matcher = version =~/^(\d+)(\.(\d+))?(\.(\d+))?(\.|-)?(.*)$/
			if (matcher.count > 0) {
				def match = matcher[0]
				String qualifier = match[7]
				if (qualifier != null) {
					qualifier = qualifier.replaceAll(/[^0-9a-zA-Z\-_]/, '_')
				}
				
				osgiVersion = new Version(
					match[1] as int,
					(match[3] as Integer)?:0,
					(match[5] as Integer)?:0,
					qualifier)
			}
			else {
				String strippedVersion = version.replaceAll(/[^0-9\.]/, '')
				osgiVersion = Version.parseVersion(strippedVersion)
			}

			// invalid callback			
			if (onInvalid != null) {
				onInvalid(osgiVersion)
			}
		}
		
		osgiVersion
	}
	
	/**
	 * Add a qualifier to the bundle version.
	 * 
	 * @param version the current bundle version
	 * @param bndConfig the bnd configuration, may be <code>null</code>
	 * @param project the Gradle project
	 * @return the modified or the given version, depending on the configuration
	 */
	static String addQualifier(String version, BndConfig bndConfig, Project project) {
		// early exit if qualifier is suppressed
		if (bndConfig?.addQualifier == false) {
			return version
		}
		
		// determine additional qualifier
		
		// default qualifier
		def addQualifier = project.platform.defaultQualifier
		
		if (bndConfig != null && project.platform.useBndHashQualifiers) {
			// strip down properties
			def props = bndConfig.properties.findAll {
				key, value ->
				key != Analyzer.BUNDLE_SYMBOLICNAME &&
				key != Analyzer.BUNDLE_VERSION &&
				key != Analyzer.BUNDLE_NAME
			}
			
			if (props) {
				// there actually are relevant properties
				
				// calculate hash from properties
				def propString = props.sort().toMapString()
				byte[] bytes = project.platform.hashCalculator(propString)
				if (bytes) {
					addQualifier = 'bnd-' + bytes.encodeBase64().toString().replaceAll(/\W/, '')
				}
			}
		}
		
		if (addQualifier) {
			// append additional qualifier
			def osgiVersion = toOsgiVersion(version)
			if (osgiVersion != null) {
				def qualifier = osgiVersion.qualifier
				if (qualifier) {
					qualifier += "-$addQualifier"
				}
				else {
					qualifier = addQualifier
				}
				Version mv = new Version(osgiVersion.major, osgiVersion.minor, osgiVersion.micro, qualifier)
				return mv.toString()
			}
		}
		
		// fall back to original version
		version
	}
	
	/**
	 * Add a qualifier to a feature version, based on the feature content.
	 *
	 * @param version the current bundle version
	 * @param feature the feature to calculate the qualifier for
	 * @param project the Gradle project
	 * @return the modified or the given version, depending on the configuration
	 */
	static String addQualifier(String version, Feature feature, Project project) {
		// early exit if qualifiers are not enabled
		if (!project.platform.useFeatureHashQualifiers) {
			return version
		}
		
		// determine qualifier
		def addQualifier
		
		// collect bundle IDs and versions
		def bundles = feature.bundles.collect { BundleArtifact bundle ->
			"${bundle.symbolicName}:${bundle.modifiedVersion}"
		}.sort()
		// collect feature IDs and versions
		def features = feature.includedFeatures.collect { Feature include ->
			"${include.id}:${include.version}"
		}.sort()
		
		def propString = [bundles: bundles, features: features].toMapString()
		byte[] bytes = project.platform.hashCalculator(propString)
		if (bytes) {
			addQualifier = 'bnd-' + bytes.encodeBase64().toString().replaceAll(/\W/, '')
		}
		
		if (addQualifier) {
			// append additional qualifier
			def osgiVersion = toOsgiVersion(version)
			if (osgiVersion != null) {
				def qualifier = osgiVersion.qualifier
				if (qualifier) {
					qualifier += "-$addQualifier"
				}
				else {
					qualifier = addQualifier
				}
				Version mv = new Version(osgiVersion.major, osgiVersion.minor, osgiVersion.micro, qualifier)
				return mv.toString()
			}
		}
		
		// fall back to original version
		version
	}
	
}