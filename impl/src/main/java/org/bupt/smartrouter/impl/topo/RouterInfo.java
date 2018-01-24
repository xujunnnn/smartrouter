/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.topo;

import java.util.LinkedHashSet;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;

public class RouterInfo {
	private List<Link> path;
	private List<NodeId> pathNode;
	public List<Link> getPath() {
		return path;
	}
	public void setPath(List<Link> path) {
		this.path = path;
	}
	
	public List<NodeId> getPathNode() {
		return pathNode;
	}
	public void setPathNode(List<NodeId> pathNode) {
		this.pathNode = pathNode;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((pathNode == null) ? 0 : pathNode.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RouterInfo other = (RouterInfo) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (pathNode == null) {
			if (other.pathNode != null)
				return false;
		} else if (!pathNode.equals(other.pathNode))
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "RouterInfo [path=" + path + ", pathNode=" + pathNode + "]";
	}
	
} 
