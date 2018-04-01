/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.topo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bwcollector.rev150105.BWCollectorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bwcollector.rev150105.GetBWInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bwcollector.rev150105.GetBWInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaycollector.rev180105.DelaycollectorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaycollector.rev180105.GetDelayInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaycollector.rev180105.GetDelayInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.smartrouter.rev150105.traffic_requirments.Requirements;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.SparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
public class TopoGraph extends SparseGraph<NodeId, Link> implements DataChangeListener{
	private static final Logger LOG = LoggerFactory.getLogger(TopoGraph.class);
	private static final long serialVersionUID = 6644118836804048053L;
	private final InventoryReader inventoryReader;
	private final DataBroker dataBroker;
	private static final String FlowID="flow:1";
	private final DelaycollectorService delaycollectorService;
	private final BWCollectorService bwCollectorService;
	private DijkstraShortestPath<NodeId, Link> routeresolver=new DijkstraShortestPath<>(this);
	private ListenerRegistration<DataChangeListener> listenerRegistration;
	private boolean refreshData = false;
	private long refreshDataDelay = 20L;
	private boolean refreshDataScheduled = false;
	private final ScheduledExecutorService topoDataChangeEventProcessor = Executors.newScheduledThreadPool(1);
	private LoadingCache<ImmutablePair<NodeId, NodeId>, RouterInfo> cache=CacheBuilder.newBuilder()
			.maximumSize(100000)
			.build(new CacheLoader<ImmutablePair<NodeId, NodeId>, RouterInfo>(){
				@Override
				public RouterInfo load(ImmutablePair<NodeId, NodeId> key) throws Exception {
					// TODO Auto-generated method stub
					return buildRouteInfo(key.getLeft(), key.getRight());
				}	
			});
	public TopoGraph(final InventoryReader inventoryReader,final DataBroker dataBroker,final DelaycollectorService delaycollectorService,final BWCollectorService bwCollectorService) {
		super();
		this.inventoryReader = inventoryReader;
		this.dataBroker = dataBroker;
		this.delaycollectorService=delaycollectorService;
		this.bwCollectorService=bwCollectorService;
	}
	
	public NodeConnectorId getAttachPoint(MacAddress macAddress){
		return inventoryReader.getHostmap().get(macAddress);
	}

	private void addNode(){
		List<Node> nodes=inventoryReader.getNodes();
		for(Node node:nodes){
			addVertex(node.getNodeId());
		}
	}

	private void addLink(){
		List<Link> links=inventoryReader.getLinks();
		for(Link link:links){
			addEdge(link,link.getSource().getSourceNode(),link.getDestination().getDestNode(), EdgeType.DIRECTED);
		}
	}
	
	public void updateTopo(){
		cache.cleanUp();
		inventoryReader.update();
		addNode();
		addLink();
		if(listenerRegistration==null){
			InstanceIdentifier<Topology> ii=InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(new TopologyId(FlowID))).build();
			listenerRegistration=dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,ii, this, DataChangeScope.SUBTREE);
		}
	}
	/**
	 * 
	 * @param src
	 * @param dst
	 * @return
	 */
	public RouterInfo getRouterInfo(NodeId src,NodeId dst){
		try {
			return cache.get(new ImmutablePair<NodeId, NodeId>(src,dst));
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	public RouterInfo getRouterInfo(NodeId src,NodeId dst,Requirements requirment){
		RouterInfo routerInfo=new RouterInfo();
		if(vertex_maps.size()==0){
			updateTopo();
		}
		List<Link> path=getAblePath(src, dst, requirment);
		routerInfo.setPath(path);
		List<NodeId> nodes=new ArrayList<>();
		for(Link link:path){
			nodes.add(link.getSource().getSourceNode());
		}
		nodes.add(dst);
		routerInfo.setPathNode(nodes);
		return routerInfo;
		
	}
	/**
	 * 
	 * @param src
	 * @param dst
	 * @param requirment
	 * @return
	 */
	public List<Link> getAblePath(NodeId src,NodeId dst,Requirements requirment){
		if(src==null || dst==null){
			return null;
		}
		List<List<Link>> result=new LinkedList<>();
		List<Link> path=new LinkedList<>();
		Set<NodeId> set=new HashSet<>();
		set.add(src);
		helper(result,path,set,src, dst, 0, Long.MAX_VALUE,requirment);
		return path;
		
	}
	public void helper(List<List<Link>>result, List<Link> path,Set<NodeId> nodes,NodeId cur,NodeId dst,long nowDelay,long nowbW,Requirements requirment){
		if(nowDelay>requirment.getDelay() || nowbW<requirment.getBW()){
			return;
		}
		if(cur.getValue().equals(dst.getValue()) && nowDelay<=requirment.getDelay() && nowbW >=requirment.getBW()){
			result.add(new ArrayList<>(path));
			return;
		}
		for(Link l:getOutEdges(cur)){
			if(nodes.contains(l.getDestination().getDestNode())){
				continue;
			}
			String nodeconnector=l.getSource().getSourceTp().getValue();
			path.add(l);
			nodes.add(l.getDestination().getDestNode());
			GetDelayInput DelayInput=new GetDelayInputBuilder().setNodeConnector(nodeconnector).build();
			GetBWInput BWInput=new GetBWInputBuilder().setNodeConnector(nodeconnector).build();
			try {
				long delay=delaycollectorService.getDelay(DelayInput).get().getResult().getDelay();
				long bandWidth=bwCollectorService.getBW(BWInput).get().getResult().getBW().longValueExact();
				helper(result,path,nodes,l.getDestination().getDestNode(), dst, nowDelay,Math.min(nowbW,bandWidth), requirment);
				path.remove(path.size()-1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	/**
	 * 
	 * @param src
	 * @param dst
	 * @return
	 */
	public RouterInfo buildRouteInfo(NodeId src,NodeId dst){
		RouterInfo routerInfo=new RouterInfo();
		if(vertex_maps.size()==0){
			updateTopo();
		}
		List<Link> path=routeresolver.getPath(src, dst);
		routerInfo.setPath(path);
		List<NodeId> nodes=new ArrayList<>();
		for(Link link:path){
			nodes.add(link.getSource().getSourceNode());
		}
		nodes.add(dst);
		routerInfo.setPathNode(nodes);
		return routerInfo;
	}
	/**
	 * 
	 * @param src
	 * @param dst
	 * @return
	 */
	public List<Link> getPath(NodeId src,NodeId dst){
		if(vertex_maps.size()==0){
			updateTopo();
		}
		return routeresolver.getPath(src, dst);		
	}
	/**
	 * 
	 * @param src
	 * @param dst
	 * @return
	 */
	public LinkedHashSet<NodeId> getPathNode(NodeId src,NodeId dst){
		LinkedHashSet<NodeId> nodes=new LinkedHashSet<>();
		if(vertex_maps.size()==0){
			updateTopo();
		}
		List<Link> path=routeresolver.getPath(src, dst);
		for(Link link:path){
			nodes.add(link.getSource().getSourceNode());
		}
		nodes.add(dst);
		return nodes;	
	}

	@Override
	public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
		// TODO Auto-generated method stub
		 if (change == null) {
		      LOG.info("In onDataChanged: No processing done as change even is null.");
		      return;
		    }
		  synchronized(this){    
			  topoDataChangeEventProcessor.schedule(new TopoDataChangeEventProcessor(),refreshDataDelay, TimeUnit.MILLISECONDS);
		}	
	}
	//update the topology graph when data changed
	private class TopoDataChangeEventProcessor implements Runnable{
		@Override
		public void run() {
			// TODO Auto-generated method stub
			updateTopo();
		}
	}
	private void registerAsDataChangeListener(){
		InstanceIdentifier<Topology> ii=InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(new TopologyId(FlowID))).build();
		listenerRegistration=dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, ii, this, DataChangeScope.BASE);
	}
	public void close(){
		listenerRegistration.close();
	}

}
