/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.flow;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.bupt.smartrouter.impl.packet.MatchLayer;
import org.bupt.smartrouter.impl.packet.Socket;
import org.bupt.smartrouter.impl.util.MyUtil;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetQueueActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetQueueActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.queue.action._case.SetQueueActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowTableRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.KnownIpProtocols;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class FlowWriter {
    private static final Logger LOG = LoggerFactory.getLogger(FlowWriter.class);
    private final DataBroker dataBroker;
    private final SalFlowService salFlowService;
    private final String FLOW_ID_PREFIX = "Smartrouter-";
    private long flowInstallationDelay;
    private short flowTableId;
    private int flowPriority;
    private int tempFlowPriority;
    private int dispatchflowPriority;
    private int tempflowIdleTimeout;
    private int tempflowHardTimeout;
    private int dispatchflowIdleTimeout;
    private int dispatchflowHardTimeout;
    
    public int getDispatchflowPriority() {
		return dispatchflowPriority;
	}
	public void setDispatchflowPriority(int dispatchflowPriority) {
		this.dispatchflowPriority = dispatchflowPriority;
	}
	public int getDispatchflowIdleTimeout() {
		return dispatchflowIdleTimeout;
	}
	public void setDispatchflowIdleTimeout(int dispatchflowIdleTimeout) {
		this.dispatchflowIdleTimeout = dispatchflowIdleTimeout;
	}
	public int getDispatchflowHardTimeout() {
		return dispatchflowHardTimeout;
	}
	public void setDispatchflowHardTimeout(int dispatchflowHardTimeout) {
		this.dispatchflowHardTimeout = dispatchflowHardTimeout;
	}
	public int getTempFlowPriority() {
		return tempFlowPriority;
	}
	public void setTempFlowPriority(int tempFlowPriority) {
		this.tempFlowPriority = tempFlowPriority;
	}
	public int getTempflowIdleTimeout() {
		return tempflowIdleTimeout;
	}
	public void setTempflowIdleTimeout(int tempflowIdleTimeout) {
		this.tempflowIdleTimeout = tempflowIdleTimeout;
	}
	public int getTempflowHardTimeout() {
		return tempflowHardTimeout;
	}
	public void setTempflowHardTimeout(int tempflowHardTimeout) {
		this.tempflowHardTimeout = tempflowHardTimeout;
	}
	private int flowIdleTimeout;
    private int flowHardTimeout;
    private AtomicLong flowIdInc = new AtomicLong();
    private AtomicLong flowCookieInc = new AtomicLong(0x2b00000000000000L);
    public FlowWriter(DataBroker dataBroker, SalFlowService salFlowService) {
		super();
		this.dataBroker = dataBroker;
		this.salFlowService = salFlowService;
	}
	public long getFlowInstallationDelay() {
		return flowInstallationDelay;
	}
	public void setFlowInstallationDelay(long flowInstallationDelay) {
		this.flowInstallationDelay = flowInstallationDelay;
	}
	public short getFlowTableId() {
		return flowTableId;
	}
	public void setFlowTableId(short flowTableId) {
		this.flowTableId = flowTableId;
	}
	public int getFlowPriority() {
		return flowPriority;
	}
	public void setFlowPriority(int flowPriority) {
		this.flowPriority = flowPriority;
	}
	public int getFlowIdleTimeout() {
		return flowIdleTimeout;
	}
	public void setFlowIdleTimeout(int flowIdleTimeout) {
		this.flowIdleTimeout = flowIdleTimeout;
	}
	public int getFlowHardTimeout() {
		return flowHardTimeout;
	}
	public void setFlowHardTimeout(int flowHardTimeout) {
		this.flowHardTimeout = flowHardTimeout;
	}
	public AtomicLong getFlowIdInc() {
		return flowIdInc;
	}
	public void setFlowIdInc(AtomicLong flowIdInc) {
		this.flowIdInc = flowIdInc;
	}
	
	public void installFlow(Socket socket,List<Link> path,NodeConnectorId ingress,NodeConnectorId egress,int index,boolean isTemp){
		NodeConnectorId inport=ingress;
		NodeConnectorId reinport=egress;
		MatchBuilder matchBuilder=buildMatch(socket, MatchLayer.L2, false);
		MatchBuilder reMatchBuilder=buildMatch(socket, MatchLayer.L2, true);
		//the ingress and egress on the same switch
		if(MyUtil.inTheSameNode(ingress, egress)){
			Match match=matchBuilder.setInPort(ingress).build();
			Match reMatch=reMatchBuilder.setInPort(egress).build();
			addInnerFlow(reMatch, MyUtil.getNodeId(egress), ingress, isTemp);
			addInnerFlow(match,MyUtil.getNodeId(ingress),egress,isTemp);	
			return;
		}
		if(path!=null && path.size()!=0){
			//install reverse flow
			for(int i=path.size()-1;i>=index;i--){
				Link link=path.get(i);
				Match reMatch=reMatchBuilder.setInPort(reinport).build();
				addInnerFlow(reMatch,link.getDestination().getDestNode(),new NodeConnectorId(link.getDestination().getDestTp().getValue()), isTemp);
				reinport=new NodeConnectorId(link.getSource().getSourceTp().getValue());
			}
			//install flow on the last node
			Match reMatch=reMatchBuilder.setInPort(reinport).build();
			addInnerFlow(reMatch, path.get(0).getSource().getSourceNode(), ingress, isTemp);
			//install flow 
			for(int i=index;i<path.size();i++){
				Link link=path.get(i);
				Match match=matchBuilder.setInPort(inport).build();
				addInnerFlow(match,link.getSource().getSourceNode(),new NodeConnectorId(link.getSource().getSourceTp().getValue()), isTemp);
				inport=new NodeConnectorId(link.getDestination().getDestTp().getValue());
			}
			//install flow on the last node
			Match match=matchBuilder.setInPort(inport).build();
			addInnerFlow(match, path.get(path.size()-1).getDestination().getDestNode(),egress, isTemp);		
		}
	}
	/**
	 * 
	 * @param socket
	 * @param ingress
	 * @param egress1
	 * @param egress2
	 * @param nodeId
	 */
	public void installBranchFlow(Socket socket,NodeConnectorId ingress,NodeConnectorId egress1,NodeConnectorId egress2,NodeId nodeId){
		socket.setProtocol(KnownIpProtocols.Tcp);
		MatchBuilder matchBuilder=buildMatch(socket, MatchLayer.L3, false);
		MatchBuilder reMatchBuilder=buildMatch(socket, MatchLayer.L3, true);
		Match match=matchBuilder.setInPort(ingress).build();
		addBranchFlow(match, egress1, egress2, nodeId);
		Match revmatch=reMatchBuilder.setInPort(egress1).build();
		addBranchFlow(revmatch, ingress, egress2, nodeId);
	}
	/**
	 * 
	 * @param socket
	 * @param path
	 * @param ingress
	 * @param egress
	 * @param queue
	 */
	public void installDispatchFlow(Socket socket,List<Link> path,NodeConnectorId ingress,NodeConnectorId egress,long queue){
			MatchBuilder matchBuilder=buildMatch(socket, MatchLayer.L4, false);
			MatchBuilder reMatchBuilder=buildMatch(socket, MatchLayer.L4, true);
			NodeConnectorId inport=ingress;
			NodeConnectorId reinport=egress;
			if(MyUtil.inTheSameNode(ingress, egress)){
				Match match=matchBuilder.setInPort(ingress).build();
				Match reMatch=reMatchBuilder.setInPort(egress).build();
				addDispatchFlow(match, egress, queue, MyUtil.getNodeId(ingress));
				addDispatchFlow(reMatch, ingress, queue, MyUtil.getNodeId(ingress));
				return;
			}
			if(path!=null && path.size()!=0){
				for(int i=path.size()-1;i>=0;i--){
					Link link=path.get(i);
					Match reMatch=reMatchBuilder.setInPort(reinport).build();
					addDispatchFlow(reMatch, new NodeConnectorId(link.getDestination().getDestTp().getValue()), queue, link.getDestination().getDestNode());
					inport=new NodeConnectorId(link.getSource().getSourceTp().getValue());
				}
				Match reMatch=reMatchBuilder.setInPort(reinport).build();
				addDispatchFlow(reMatch, ingress, queue, path.get(0).getSource().getSourceNode());
				for(Link link:path){
					Match match=matchBuilder.setInPort(inport).build();
					addDispatchFlow(match, new NodeConnectorId(link.getSource().getSourceTp().getValue()), queue,link.getSource().getSourceNode());
					inport=new NodeConnectorId(link.getDestination().getDestTp().getValue());
				}
				Match match=matchBuilder.setInPort(inport).build();
				addDispatchFlow(match, egress, queue,path.get(path.size()-1).getDestination().getDestNode());
			}		
	}
	
	public void addInnerFlow(Match match,NodeId nodeId,NodeConnectorId egress,boolean isTemp){
		writeFlowToSwitch(nodeId, creatOutPutFlow(match, egress,isTemp));
	}
	public void addBranchFlow(Match match,NodeConnectorId egress1,NodeConnectorId egress2,NodeId nodeId){
		writeFlowToSwitch(nodeId, createBranchFlow(match, egress1, egress2));
	}
	public void addDispatchFlow(Match match,NodeConnectorId egress,long queue,NodeId nodeId){
		writeFlowToSwitch(nodeId, creatDispatchFlow(match, egress, queue));
	}
	/**
	 * create flow dispatch the packet go to different queue
	 * @param match
	 * @param destPort
	 * @param queue
	 * @return
	 */
	private Flow creatDispatchFlow(Match match,NodeConnectorId destPort,long queue){
		FlowBuilder flowBuilder=new FlowBuilder().setTableId(flowTableId);
		flowBuilder.setId(new FlowId(Long.toString(flowBuilder.hashCode())));
		flowBuilder.setMatch(match);
		Action setQueueAction=new ActionBuilder().setOrder(0)
				.setAction(new SetQueueActionCaseBuilder()
				.setSetQueueAction(new SetQueueActionBuilder()
				.setQueueId(queue)
				.build())
				.build())
				.build();
		Uri destPortUri = new Uri(destPort.getValue());
		Action outputAction=new ActionBuilder().setOrder(1)
				.setAction(new OutputActionCaseBuilder()
				.setOutputAction(new OutputActionBuilder()
				.setOutputNodeConnector(destPortUri)
				.setMaxLength(0xffff)
				.build())
				.build())
				.build();
		List<Action> actions=new ArrayList<>();
		actions.add(setQueueAction);
		actions.add(outputAction);
		ApplyActions applyActions = new ApplyActionsBuilder().setAction(actions).build();
		Instruction applyActionsInstruction = new InstructionBuilder() //
	               .setOrder(0)
	               .setInstruction(new ApplyActionsCaseBuilder()//
	               .setApplyActions(applyActions) //
	               .build()) //
	               .build();
		flowBuilder.setInstructions(new InstructionsBuilder()
				   .setInstruction(ImmutableList.of(applyActionsInstruction)).build())
		 		   .setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
		 		   .setFlags(new FlowModFlags(false, false, false, false, false));
		flowBuilder.setHardTimeout(dispatchflowHardTimeout)
	 			   .setIdleTimeout(dispatchflowIdleTimeout)
	 			   .setPriority(dispatchflowPriority);
		return flowBuilder.build();
		
	}
	/**
	 * create out put flow 
	 * @param match
	 * @param destPort
	 * @param isTemp
	 * @return
	 */
	private Flow creatOutPutFlow(Match match,NodeConnectorId destPort,boolean isTemp){
		FlowBuilder flowBuilder=new FlowBuilder().setTableId(flowTableId);
		if(isTemp){
			flowBuilder.setFlowName("tempoutflow");
		}
		flowBuilder.setId(new FlowId(Long.toString(flowBuilder.hashCode())));
		flowBuilder.setMatch(match);
		Uri destPortUri = new Uri(destPort.getValue());
		Action outputAction=new ActionBuilder().setOrder(0)
				.setAction(new OutputActionCaseBuilder()
				.setOutputAction(new OutputActionBuilder()
				.setOutputNodeConnector(destPortUri)
				.setMaxLength(0xffff)
				.build())
				.build())
				.build();
		 ApplyActions applyActions = new ApplyActionsBuilder().setAction(ImmutableList.of(outputAction))
	                .build();
		 Instruction applyActionsInstruction = new InstructionBuilder() //
	                .setOrder(0)
	                .setInstruction(new ApplyActionsCaseBuilder()//
	                        .setApplyActions(applyActions) //
	                        .build()) //
	                .build();
		 flowBuilder.setInstructions(new InstructionsBuilder()
				 	.setInstruction(ImmutableList.of(applyActionsInstruction)).build())
		 			.setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
		 			.setFlags(new FlowModFlags(false, false, false, false, false));
		 if(isTemp){
			 flowBuilder.setHardTimeout(tempflowHardTimeout)
			 			.setIdleTimeout(tempflowIdleTimeout)
			 			.setPriority(tempFlowPriority);
		 }
		 else {
			 flowBuilder.setHardTimeout(flowHardTimeout)
	 			.setIdleTimeout(flowIdleTimeout)
	 			.setPriority(flowPriority);
		}
		return flowBuilder.build();
		
	}
	/**
	 * create branch flow
	 * @param match
	 * @param destPort1
	 * @param destPort2
	 * @return
	 */
	private Flow createBranchFlow(Match match,NodeConnectorId destPort1,NodeConnectorId destPort2){
		FlowBuilder flowBuilder=new FlowBuilder().setTableId(flowTableId);
		flowBuilder.setFlowName("branchFlow");
		flowBuilder.setId(new FlowId(Long.toString(flowBuilder.hashCode())));
		flowBuilder.setMatch(match);
		Uri destPort1Uri = new Uri(destPort1.getValue());
		Action outputAction1=new ActionBuilder().setOrder(0)
				.setAction(new OutputActionCaseBuilder()
				.setOutputAction(new OutputActionBuilder()
				.setOutputNodeConnector(destPort1Uri)
				.setMaxLength(0xffff)
				.build())
				.build())
				.build();
		Uri destPort2Uri = new Uri(destPort2.getValue());
		Action outputAction2=new ActionBuilder().setOrder(1)
				.setAction(new OutputActionCaseBuilder()
				.setOutputAction(new OutputActionBuilder()
				.setOutputNodeConnector(destPort2Uri)
				.setMaxLength(0xffff)
				.build())
				.build())
				.build();
		List<Action> actions=new LinkedList<>();
		actions.add(outputAction1);
		actions.add(outputAction2);
		ApplyActions applyActions = new ApplyActionsBuilder().setAction(actions).build();
		Instruction applyActionsInstruction = new InstructionBuilder() //
	                .setOrder(0)
	                .setInstruction(new ApplyActionsCaseBuilder()//
	                        .setApplyActions(applyActions) //
	                        .build()) //
	                .build();
		flowBuilder.setInstructions(new InstructionsBuilder()
				 	.setInstruction(ImmutableList.of(applyActionsInstruction)).build())
		 			.setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
		 			.setFlags(new FlowModFlags(false, false, false, false, false))
					.setHardTimeout(tempflowHardTimeout)
					.setIdleTimeout(tempflowIdleTimeout)
					.setPriority(tempFlowPriority);
		return flowBuilder.build();	
	}
	/**
	 * build match for the socket ,reverse=true means fanzhuan socket
	 * @param socket
	 * @param matchLayer
	 * @param reverse
	 * @return
	 */
	private MatchBuilder buildMatch(Socket socket,MatchLayer matchLayer,boolean reverse){
		if(socket==null){
			return null;
		}
		MatchBuilder builder=new MatchBuilder();
		Socket matchSocket;
		if(!reverse)
			matchSocket=socket;
		else
			matchSocket=socket.reverse();
			builder.setEthernetMatch(new EthernetMatchBuilder()
					.setEthernetSource(new EthernetSourceBuilder().setAddress(matchSocket.getSrcMac()).build())
					.setEthernetDestination(new EthernetDestinationBuilder().setAddress(matchSocket.getDestMac()).build())
					.setEthernetType(new EthernetTypeBuilder()
					.setType(new EtherType(2048l))
					.build())
					.build());
			builder.setIpMatch(new IpMatchBuilder()
						.setIpProtocol((short)matchSocket.getProtocol().getIntValue())
						.build());
			if(matchLayer==MatchLayer.L3 || matchLayer==MatchLayer.L4){
				if(matchSocket.getSrcAddress()==null || matchSocket.getDestAddress()==null || matchSocket.getProtocol()==null){
					return null;
				}
				builder.setIpMatch(new IpMatchBuilder()
						.setIpProtocol((short)matchSocket.getProtocol().getIntValue())
						.build())
						.setLayer3Match(new Ipv4MatchBuilder()
						.setIpv4Source(new Ipv4Prefix(matchSocket.getSrcAddress().getValue()+"/32"))
						.setIpv4Destination(new Ipv4Prefix(matchSocket.getDestAddress().getValue()+"/32"))
						.build());
			}
			if(matchLayer==MatchLayer.L4){
				builder.setLayer4Match(new TcpMatchBuilder()
						.setTcpSourcePort(new PortNumber(matchSocket.getSrcPort()))
						.setTcpDestinationPort(new PortNumber(matchSocket.getDestPort()))
						.build());		
			}
		return builder;
	}
	
	
	
    /**
     * Starts and commits data change transaction which modifies provided
     * flow path with supplied body.
     */
    private Future<RpcResult<AddFlowOutput>> writeFlowToSwitch(NodeId nodeId, Flow flow) {
        InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.<Nodes>builder(Nodes.class)
                .<Node, NodeKey>child(Node.class, new NodeKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId(nodeId.getValue()))).build();
        InstanceIdentifier<Table> tableInstanceId = nodeInstanceId
                .<FlowCapableNode>augmentation(FlowCapableNode.class)
                .<Table, TableKey>child(Table.class, new TableKey(flowTableId));
        InstanceIdentifier<Flow> flowPath = tableInstanceId.<Flow, FlowKey>child(Flow.class,
                new FlowKey(new FlowId(FLOW_ID_PREFIX+String.valueOf(flowIdInc.getAndIncrement()))));
        final AddFlowInputBuilder builder = new AddFlowInputBuilder(flow).setNode(new NodeRef(nodeInstanceId))
                .setFlowTable(new FlowTableRef(tableInstanceId)).setFlowRef(new FlowRef(flowPath))
                .setTransactionUri(new Uri(flow.getId().getValue()));
        return salFlowService.addFlow(builder.build());
    }
    
}
