/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.util;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

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
}
