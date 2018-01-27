/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.util;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
public class MyUtil {
	public static NodeId getNodeId(NodeConnectorId nodeConnectorId){
		String []info=nodeConnectorId.getValue().split(":");
		return new NodeId(info[0]+":"+info[1]);
	}
	public static boolean inTheSameNode(NodeConnectorId connectorId1,NodeConnectorId connectorId2){
		if(getNodeId(connectorId1).equals(getNodeId(connectorId2))){
			return true;
		}
		return false;
	}
	public static NodeConnectorRef getRef(NodeConnectorId nodeConnectorId){
		org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId nodeId=new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId(getNodeId(nodeConnectorId).getValue());
		InstanceIdentifier<NodeConnector> identifier=InstanceIdentifier.builder(Nodes.class)
				.child(Node.class, new NodeKey(nodeId))
				.child(NodeConnector.class, new NodeConnectorKey(nodeConnectorId))
				.build();
		return new NodeConnectorRef(identifier);
	}
}
