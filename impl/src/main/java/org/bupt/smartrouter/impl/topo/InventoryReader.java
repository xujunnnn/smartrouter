/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.topo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;

public class InventoryReader{
	private static final Logger LOG = LoggerFactory.getLogger(InventoryReader.class);
	private static final String SWITCHPREFIX="openflow";
	private static final String HostPREFIX="host";
	private static final String FlowID="flow:1";
	private final DataBroker dataBroker;
	private List<Node> nodes;
	private List<Link> links;
	private Map<MacAddress,NodeConnectorId> hostmap;
	private boolean refreshData = false;
	private long refreshDataDelay = 20L;
	private boolean refreshDataScheduled = false;
	private final ScheduledExecutorService topoDataChangeEventProcessor = Executors.newScheduledThreadPool(1);
	private ListenerRegistration<DataChangeListener> listenerRegistration;
	public InventoryReader(final DataBroker dataBroker) {
		super();
		this.dataBroker = dataBroker;
		this.nodes = new CopyOnWriteArrayList<>();
		this.links = new CopyOnWriteArrayList<>();
		this.hostmap=new ConcurrentHashMap<>();
	}
	
	
	public Map<MacAddress, NodeConnectorId> getHostmap() {
		return hostmap;
	}


	public void setHostmap(Map<MacAddress, NodeConnectorId> hostmap) {
		this.hostmap = hostmap;
	}


	public List<Node> getNodes() {
		return nodes;
	}

	public void setNodes(List<Node> nodes) {
		this.nodes = nodes;
	}

	public List<Link> getLinks() {
		return links;
	}

	public void setLinks(List<Link> links) {
		this.links = links;
	}
	

	public void setRefreshData(boolean refreshData) {
		this.refreshData = refreshData;
	}


	public void update(){
		nodes.clear();
		links.clear();
		setRefreshData(true);
		readInventory();
	}
	
	public void readInventory(){
		if(!refreshData)
			return;
		synchronized (this) {
			Topology topology=null;
			if(!refreshData)
				return;
			InstanceIdentifier<Topology> ii=InstanceIdentifier.
											builder(NetworkTopology.class).
											child(Topology.class, new TopologyKey(new TopologyId(FlowID))).
											build();
			ReadOnlyTransaction rx=dataBroker.newReadOnlyTransaction();
			CheckedFuture<Optional<Topology>, ReadFailedException>	checkedFuture=rx.read(LogicalDatastoreType.OPERATIONAL, ii);
			try {
				Optional<Topology> optional=checkedFuture.get(1000, TimeUnit.MILLISECONDS);
				if(optional.isPresent()){
					topology=optional.get();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				LOG.error("Failed to read topology from Operation data store.");
				throw new RuntimeException("Failed to read nodes from Operation data store.", e);
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				LOG.error("Failed to read topology from Operation data store.");
				throw new RuntimeException("Failed to read nodes from Operation data store.", e);
			} catch (TimeoutException e) {
				// TODO Auto-generated catch block
				LOG.error("Failed to read topology from Operation data store.");
				throw new RuntimeException("Failed to read nodes from Operation data store.", e);
			}
			if(topology!=null){
				if(topology.getNode()!=null){
					for(Node node:topology.getNode()){
						if(isSwitch(node.getNodeId())){
							nodes.add(node);
						}
						else if (isHost(node.getNodeId())) {
							HostNode hostNode=node.getAugmentation(HostNode.class);
							MacAddress address=hostNode.getAddresses().get(0).getMac();
							NodeConnectorId nodeConnector=new NodeConnectorId(hostNode.getAttachmentPoints().get(0).getTpId().getValue());
							hostmap.put(address,nodeConnector);
						}
					}
				}
				if(topology.getLink()!=null){
					for(Link link:topology.getLink()){
						if(isInnerLink(link)){
							links.add(link);
						}
					}
				}
			}
			 rx.close();
	         refreshData = false;
	        
		}
	}
	//check whether the node is a switch
	private boolean isSwitch(NodeId nodeid){
		if(nodeid.getValue().contains(SWITCHPREFIX))
			return true;
		return false;
	}
	private boolean isHost(NodeId nodeid){
		if(nodeid.getValue().contains(HostPREFIX))
			return true;
		return false;
	}
	private boolean isInnerLink(Link link){
		if(isSwitch(link.getSource().getSourceNode()) && isSwitch(link.getDestination().getDestNode())){
			return true;
		}
		return false;
	}
	
	
	
	
	
}
