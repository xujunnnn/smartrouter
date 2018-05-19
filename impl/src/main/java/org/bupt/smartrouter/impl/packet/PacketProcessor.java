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
	private DispatchPacketProcessor dispatchPacketProcessor;
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
	
	
	public DispatchPacketProcessor getDispatchPacketProcessor() {
		return dispatchPacketProcessor;
	}


	public void setDispatchPacketProcessor(DispatchPacketProcessor dispatchPacketProcessor) {
		this.dispatchPacketProcessor = dispatchPacketProcessor;
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
        if(ipv4Packet.getProtocol()==KnownIpProtocols.Experimentation1)
        	return;
	        //Construct the socket information
		Socket socket=Socket.getSocket(ethernetPacket,ipv4Packet,packetReceived);
		handlePacket(socket, packetReceived.getPayload());
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		if(ipv4Packet.getProtocol()==KnownIpProtocols.Tcp){
//			socket.setDscp(1);
//			dispatchPacketProcessor.handlerSocket(packetReceived.getPayload(), socket);
//		}
//		
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
		        NodeConnectorId branchIngress=null;
 		        NodeConnectorId branchEgress1=null;
		        NodeConnectorId branchEgress2=null;
		        branchNode=findFocus(nodes, servernodes);
		        if(branchNode==null){
		        	return;
		        }
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
		        flowWriter.installFlow(socket, path, srcnodeconnector, dstnodeconnector,0,false);	
		        if(socket.getProtocol()==KnownIpProtocols.Tcp){
		        	flowWriter.installBranchFlow(socket, branchIngress, branchEgress1, branchEgress2, branchNode);
		        }
		        if(branchEgress2!=serverNodeconnector){
		        	flowWriter.installFlow(socket, serverpath, new NodeConnectorId(serverpath.get(index).getDestination().getDestTp().getValue()), serverNodeconnector, index+1, true);
		        }		
			}
		});
	}
	/**
	 * get the focus point of the two path
	 * @param path1
	 * @param path2
	 * @return
	 */
	private NodeId findFocus(List<NodeId> path1,List<NodeId> path2){
		NodeId nodeId=null;
		for(int i=path2.size()-1;i>=0;i--){
			if(path1.contains(path2.get(i))){
				nodeId=path2.get(i);
				return nodeId;
			}
		}
		return nodeId;
		
	}

}
