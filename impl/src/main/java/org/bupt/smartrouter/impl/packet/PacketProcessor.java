/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.packet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.bupt.smartrouter.impl.flow.FlowWriter;
import org.bupt.smartrouter.impl.topo.RouterInfo;
import org.bupt.smartrouter.impl.topo.TopoGraph;
import org.bupt.smartrouter.impl.util.MyUtil;
import org.opendaylight.l2switch.packethandler.decoders.utils.BitBufferHelper;
import org.opendaylight.l2switch.packethandler.decoders.utils.BufferException;
import org.opendaylight.l2switch.packethandler.decoders.utils.NetUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
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
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
public class PacketProcessor implements Ipv4PacketListener{
	private ExecutorService service;
	private final FlowWriter flowWriter;
	private final TopoGraph topoGraph;
	private NodeId serverNode;
	private PacketProcessingService packetProcessingService;
	private NodeConnectorId serverNodeconnector;
	private MacAddress serverMac;
	private static final String BroadCast="ff:ff:ff:ff:ff:ff";
	public PacketProcessor(FlowWriter flowWriter, TopoGraph topoGraph) {
		super();
		this.flowWriter = flowWriter;
		this.topoGraph = topoGraph;
	}
	
	public PacketProcessingService getPacketProcessingService() {
		return packetProcessingService;
	}

	public void setPacketProcessingService(PacketProcessingService packetProcessingService) {
		this.packetProcessingService = packetProcessingService;
	}

	public ExecutorService getService() {
		return service;
	}

	public void setService(ExecutorService service) {
		this.service = service;
	}

	public NodeId getServerNode() {
		return serverNode;
	}

	public void setServerNode(NodeId serverNode) {
		this.serverNode = serverNode;
	}

	public NodeConnectorId getServerNodeconnector() {
		return serverNodeconnector;
	}

	public void setServerNodeconnector(NodeConnectorId serverNodeconnector) {
		this.serverNodeconnector = serverNodeconnector;
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
        if(BroadCast.equals(ethernetPacket.getDestinationMac().getValue())){
        	return;
        }
        Socket socket=new Socket();
        socket.setSrcMac(ethernetPacket.getSourceMac());
        socket.setDestMac(ethernetPacket.getDestinationMac());
        socket.setSrcAddress(ipv4Packet.getSourceIpv4());
        socket.setDestAddress(ipv4Packet.getDestinationIpv4());
        socket.setProtocol(ipv4Packet.getProtocol());
        byte[] payload=packetReceived.getPayload();
        if(ipv4Packet.getProtocol()==KnownIpProtocols.Tcp){
        	
        	int bitOffset = ipv4Packet.getPayloadOffset() * NetUtils.NumBitsInAByte;
        	try {
				int srcPort=BitBufferHelper.getInt(BitBufferHelper.getBits(payload, bitOffset, 16));
				socket.setSrcPort(srcPort);
				int destPort=BitBufferHelper.getInt(BitBufferHelper.getBits(payload, bitOffset+16, 16));
				socket.setDestPort(destPort);
			} catch (BufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        else if(ipv4Packet.getProtocol()==KnownIpProtocols.Udp){
        	int bitOffset = ipv4Packet.getPayloadOffset() * NetUtils.NumBitsInAByte;
        	try {
				int srcPort=BitBufferHelper.getInt(BitBufferHelper.getBits(payload, bitOffset, 16));
				socket.setSrcPort(srcPort);
				int destPort=BitBufferHelper.getInt(BitBufferHelper.getBits(payload, bitOffset+16, 16));
				socket.setDestPort(destPort);
			} catch (BufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        handlePacket(socket,payload);
        	
	}
	/**
	 * 
	 * @param payload
	 * @param ingress
	 * @param egress
	 */
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
	/**
	 * 
	 * @param socket
	 */
	public void handlePacket(Socket socket,byte []payload){
		service.execute(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				MacAddress srcAddress=socket.getSrcMac();
		        MacAddress dstAddress=socket.getDestMac();
		        NodeConnectorId srcnodeconnector=topoGraph.getAttachPoint(srcAddress);
		        NodeConnectorId dstnodeconnector=topoGraph.getAttachPoint(dstAddress);
		        if(srcnodeconnector==null ||dstnodeconnector==null)
		        	return;
		        NodeId src=MyUtil.getNodeId(srcnodeconnector);
		        NodeId dst=MyUtil.getNodeId(dstnodeconnector);
		        sendPacketOut(payload, MyUtil.getRef(srcnodeconnector), MyUtil.getRef(dstnodeconnector));
		        RouterInfo routerInfo=topoGraph.getRouterInfo(src, dst);
		        List<Link> path=routerInfo.getPath();
		        List<NodeId> nodes=routerInfo.getPathNode();
		        RouterInfo serverRouter=topoGraph.getRouterInfo(src, serverNode);
		        List<Link> serverpath=serverRouter.getPath();
		        List<NodeId> servernodes=serverRouter.getPathNode();
		        NodeId branchNode=null;
		        for(int i=servernodes.size()-1;i>=0;i--){
		        	if(nodes.contains(servernodes.get(i))){
		        		branchNode=servernodes.get(i);
		        		break;
		        	}
		        }
		        NodeConnectorId branchIngress=null;
 		        NodeConnectorId branchEgress1=null;
		        NodeConnectorId branchEgress2=null;
		        int index=0;
		        for(Link link:path){
		        	if(branchNode.equals(link.getDestination().getDestNode())){
		        		branchIngress=new NodeConnectorId(link.getDestination().getDestTp().getValue());
		        	}
		        	if(branchNode.equals(link.getSource().getSourceNode())){
		        		branchEgress1=new NodeConnectorId(link.getSource().getSourceTp());
		        		break;
		        	}
		        	index++;
		        }
		        for(Link link:serverpath){
		        	if(branchNode.equals(link.getSource().getSourceNode())){
		        		branchEgress2=new NodeConnectorId(link.getSource().getSourceTp());
		        		break;
		        	}
		        }
		        if(branchEgress1==null){
		        	branchEgress1=dstnodeconnector;
		        }
		        if(branchEgress2==null){
		        	branchEgress2=serverNodeconnector;
		        }
		        if(branchIngress==null){
		        	branchIngress=srcnodeconnector;
		        }
		        flowWriter.installFlow(socket, path, srcnodeconnector, dstnodeconnector, false);	
		        if(socket.getProtocol()!=KnownIpProtocols.Tcp && socket.getProtocol()!=KnownIpProtocols.Udp){
		        	return;
		        }
		        flowWriter.installBranchFlow(socket, branchIngress, branchEgress1, branchEgress2, branchNode);
		        if(branchEgress2!=serverNodeconnector){
		        	flowWriter.installFlow(socket, serverpath, new NodeConnectorId(serverpath.get(index).getDestination().getDestTp().getValue()), serverNodeconnector, index+1, true,false);
		        }		
			}
		});
	}

}
