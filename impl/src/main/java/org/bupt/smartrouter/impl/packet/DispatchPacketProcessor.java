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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Dscp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.packet.chain.packet.RawPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.received.packet.chain.packet.EthernetPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.KnownIpProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.ipv4.packet.received.packet.chain.packet.Ipv4Packet;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;

public class DispatchPacketProcessor implements Ipv4PacketListener{
	private final NodeConnectorId serverNodeConnector;
	private final TopoGraph topoGraph;
	private final FlowWriter flowWriter;
	private ExecutorService service;
	public ExecutorService getService() {
		return service;
	}
	public void setService(ExecutorService service) {
		this.service = service;
	}
	public DispatchPacketProcessor(final NodeConnectorId serverNodeConnector, final TopoGraph topoGraph, final FlowWriter flowWriter) {
		super();
		this.serverNodeConnector = serverNodeConnector;
		this.topoGraph = topoGraph;
		this.flowWriter = flowWriter;
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
				handlerSocket(socket, ipv4Packet.getDscp());
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
				handlerSocket(socket, ipv4Packet.getDscp());
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
	public void handlerSocket(Socket socket,Dscp dscp){
		NodeConnectorId srcnodeconnector=topoGraph.getAttachPoint(socket.getSrcMac());
	 	NodeConnectorId dstnodeconnector=topoGraph.getAttachPoint(socket.getDestMac());
        if(srcnodeconnector==null ||dstnodeconnector==null)
        	return;
        NodeId src=MyUtil.getNodeId(srcnodeconnector);
        NodeId dst=MyUtil.getNodeId(dstnodeconnector);
        RouterInfo routerInfo=topoGraph.getRouterInfo(src, dst);
        List<Link> path=routerInfo.getPath();
        flowWriter.installDispatchFlow(socket, path, srcnodeconnector, dstnodeconnector,dscp.getValue());
	}

}
