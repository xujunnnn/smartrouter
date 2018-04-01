/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.packet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.bupt.smartrouter.impl.flow.FlowWriter;
import org.bupt.smartrouter.impl.topo.RouterInfo;
import org.bupt.smartrouter.impl.topo.TopoGraph;
import org.bupt.smartrouter.impl.util.MyUtil;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.l2switch.packethandler.decoders.utils.BitBufferHelper;
import org.opendaylight.l2switch.packethandler.decoders.utils.BufferException;
import org.opendaylight.l2switch.packethandler.decoders.utils.NetUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Dscp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.packet.chain.packet.RawPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.received.packet.chain.packet.EthernetPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.KnownIpProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.ipv4.packet.received.packet.chain.packet.Ipv4Packet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.smartrouter.rev150105.TrafficRequirments;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.smartrouter.rev150105.traffic_requirments.Requirements;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;

public class DispatchPacketProcessor implements Ipv4PacketListener,DataChangeListener{
	private final DataBroker dataBroker;
	private static final Logger LOGGER=LoggerFactory.getLogger(DispatchPacketProcessor.class);
	private Map<String, Requirements> requirementMap=new ConcurrentHashMap<>();;
	private final NodeConnectorId serverNodeConnector;
	private final TopoGraph topoGraph;
	private final FlowWriter flowWriter;
	private PacketProcessingService packetProcessingService;
	private ExecutorService service;
	public ExecutorService getService() {
		return service;
	}
	public void setService(ExecutorService service) {
		this.service = service;
	}
	public PacketProcessingService getPacketProcessingService() {
		return packetProcessingService;
	}
	public void setPacketProcessingService(PacketProcessingService packetProcessingService) {
		this.packetProcessingService = packetProcessingService;
	}
	public DispatchPacketProcessor(final DataBroker dataBroker,final NodeConnectorId serverNodeConnector, final TopoGraph topoGraph, final FlowWriter flowWriter) {
		super();
		this.dataBroker=dataBroker;
		this.serverNodeConnector = serverNodeConnector;
		this.topoGraph = topoGraph;
		this.flowWriter = flowWriter;
	}
	public void initMap(){
		TrafficRequirments trafficRequirments;
		ReadOnlyTransaction rx=dataBroker.newReadOnlyTransaction();
		InstanceIdentifier<TrafficRequirments> ii=InstanceIdentifier.builder(TrafficRequirments.class).build();
		try {
			Optional<TrafficRequirments> optional=rx.read(LogicalDatastoreType.CONFIGURATION, ii).get();
			if(optional.isPresent()){
				trafficRequirments=optional.get();
				for(Requirements requirements:trafficRequirments.getRequirements()){
					requirementMap.put(requirements.getName(), requirements);
				}
			}
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onIpv4PacketReceived(Ipv4PacketReceived packetReceived) {
		// TODO Auto-generated method stub
		RawPacket rawPacket = null;
        EthernetPacket ethernetPacket = null;
        Ipv4Packet ipv4Packet=null;
        for (PacketChain packetChain : packetReceived.getPacketChain()) {
            if (packetChain.getPacket() instanceof RawPacket) {
                rawPacket = (RawPacket) packetChain.getPacket();
            } else if (packetChain.getPacket() instanceof EthernetPacket) {
                ethernetPacket = (EthernetPacket) packetChain.getPacket();
            } else if (packetChain.getPacket() instanceof Ipv4Packet) {
                ipv4Packet = (Ipv4Packet) packetChain.getPacket();
            }
        }
        if (rawPacket == null || ethernetPacket == null || ipv4Packet == null) {
            return;
        }
        if(ipv4Packet.getProtocol()==KnownIpProtocols.Experimentation1)
        	return;
        if(!serverNodeConnector.equals(rawPacket.getIngress().getValue().firstKeyOf(NodeConnector.class).getId())){
        	return;
        }
        Socket socket=new Socket();
        socket.setSrcMac(ethernetPacket.getSourceMac());
        socket.setDestMac(ethernetPacket.getDestinationMac());
        socket.setSrcAddress(ipv4Packet.getSourceIpv4());
        socket.setDestAddress(ipv4Packet.getDestinationIpv4());
        if(ipv4Packet.getProtocol()==KnownIpProtocols.Tcp){
        	socket.setProtocol(KnownIpProtocols.Tcp);
        	byte[] payload=packetReceived.getPayload();
        	int bitOffset = ipv4Packet.getPayloadOffset() * NetUtils.NumBitsInAByte;
        	try {
				int srcPort=BitBufferHelper.getInt(BitBufferHelper.getBits(payload, bitOffset, 16));
				socket.setSrcPort(srcPort);
				int destPort=BitBufferHelper.getInt(BitBufferHelper.getBits(payload, bitOffset+16, 16));
				socket.setDestPort(destPort);
				handlerSocket(payload,socket, ipv4Packet.getDscp());
			} catch (BufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	
        }
        else if(ipv4Packet.getProtocol()==KnownIpProtocols.Udp){
        	socket.setProtocol(KnownIpProtocols.Udp);
        	byte[] payload=packetReceived.getPayload();
        	int bitOffset = ipv4Packet.getPayloadOffset() * NetUtils.NumBitsInAByte;
        	try {
				int srcPort=BitBufferHelper.getInt(BitBufferHelper.getBits(payload, bitOffset, 16));
				socket.setSrcPort(srcPort);
				int destPort=BitBufferHelper.getInt(BitBufferHelper.getBits(payload, bitOffset+16, 16));
				socket.setDestPort(destPort);
				handlerSocket(payload,socket, ipv4Packet.getDscp());
			} catch (BufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
	}
	/**
	 * 
	 * @param socket
	 * @param dscp
	 */
	public void handlerSocket(byte[] payload,Socket socket,Dscp dscp){
		Requirements requirement;
		requirement=requirementMap.get(dscp.getValue().toString());
		if(requirement==null){
			LOGGER.debug("no matched requirement find");
			return;
		}
		NodeConnectorId srcnodeconnector=topoGraph.getAttachPoint(socket.getSrcMac());
	 	NodeConnectorId dstnodeconnector=topoGraph.getAttachPoint(socket.getDestMac());
        if(srcnodeconnector==null ||dstnodeconnector==null)
        	return;
        NodeId src=MyUtil.getNodeId(srcnodeconnector);
        NodeId dst=MyUtil.getNodeId(dstnodeconnector);  
        RouterInfo routerInfo=topoGraph.getRouterInfo(src, dst,requirement);
        List<Link> path=routerInfo.getPath();
        sendPacketOut(payload, MyUtil.getRef(srcnodeconnector), MyUtil.getRef(dstnodeconnector));
        flowWriter.installDispatchFlow(socket, path, srcnodeconnector, dstnodeconnector,requirement.getPriority());
	}
	
	public void sendPacketOut(byte[] payload,NodeConnectorRef ingress,NodeConnectorRef egress){
		if(ingress==null ||egress==null){
			return;
		}
		InstanceIdentifier<Node> egressNode=egress.getValue().firstIdentifierOf(Node.class);
		TransmitPacketInput input=new TransmitPacketInputBuilder()
				.setIngress(null)
				.setEgress(egress)
				.setNode(new NodeRef(egressNode))
				.setPayload(payload)
				.build();
		packetProcessingService.transmitPacket(input);
	}
	
	public void registerAsDataChangerListener(){
		InstanceIdentifier<Requirements> identifier=InstanceIdentifier.builder(TrafficRequirments.class)
				.child(Requirements.class)
				.build();
		dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, identifier, this,DataChangeScope.BASE);
		
	}
	@Override
	public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
		// TODO Auto-generated method stub
		if(change==null){
			return;
		}
		Map<InstanceIdentifier<?>, DataObject> created=change.getCreatedData();
		Set<InstanceIdentifier<?>> keySet=created.keySet();
		if(keySet!=null && !keySet.isEmpty()){
			for(InstanceIdentifier<?> ii:keySet){
				if(Requirements.class.isAssignableFrom(ii.getTargetType())){
					InstanceIdentifier<Requirements> reIdentifier=(InstanceIdentifier<Requirements> )ii;
					ReadOnlyTransaction rx=dataBroker.newReadOnlyTransaction();
					CheckedFuture<Optional<Requirements>, ReadFailedException> future=rx.read(LogicalDatastoreType.CONFIGURATION,reIdentifier);;
					try {
						Optional<Requirements> optional=future.get();
						if(optional.isPresent()){
							Requirements requirements=optional.get();
							requirementMap.put(requirements.getName(), requirements);
						}
					} catch (InterruptedException | ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			}
		}
		
	}

}
