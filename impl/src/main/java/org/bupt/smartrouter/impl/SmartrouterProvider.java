/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bupt.smartrouter.impl.flow.FlowWriter;
import org.bupt.smartrouter.impl.packet.DispatchPacketProcessor;
import org.bupt.smartrouter.impl.packet.PacketProcessor;
import org.bupt.smartrouter.impl.topo.InventoryReader;
import org.bupt.smartrouter.impl.topo.TopoGraph;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.smartrouter.config.rev140528.SmartRouterConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bwcollector.rev150105.BWCollectorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaycollector.rev180105.DelaycollectorService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartrouterProvider {
    private static final Logger LOG = LoggerFactory.getLogger(SmartrouterProvider.class);
    private ExecutorService service=Executors.newFixedThreadPool(20);
    private final DataBroker dataBroker;
    private final SalFlowService salFlowService;
    private final BWCollectorService bwCollectorService;
    private final DelaycollectorService delaycollectorService;
    private final NotificationProviderService notificationService;
    private final PacketProcessingService packetProcessingService;
    private final RpcProviderRegistry registry;
    private SmartRouterConfig config;
    private ListenerRegistration listenerRegistration;
    private ListenerRegistration listenerRegistration2;
    public SmartrouterProvider(final DataBroker dataBroker,final SalFlowService salFlowService,final NotificationProviderService notificationService,final PacketProcessingService packetProcessingService,final RpcProviderRegistry registry, SmartRouterConfig config) {
        this.dataBroker = dataBroker;
        this.salFlowService=salFlowService;
        this.notificationService=notificationService;
        this.packetProcessingService=packetProcessingService;
        this.config=config;
        this.registry=registry;
        this.bwCollectorService=registry.getRpcService(BWCollectorService.class);
        this.delaycollectorService=registry.getRpcService(DelaycollectorService.class);
    }
    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("SmartrouterProvider Session Initiated");
        InventoryReader inventoryReader=new InventoryReader(dataBroker);
        FlowWriter flowWriter=new FlowWriter(dataBroker, salFlowService);
        flowWriter.setFlowHardTimeout(config.getFlowHardTimeout());
        flowWriter.setFlowIdleTimeout(config.getFlowIdleTimeout());
        flowWriter.setFlowInstallationDelay(config.getFlowInstallationDelay());
        flowWriter.setFlowTableId(config.getFlowTableId());
        flowWriter.setFlowPriority(config.getFlowPriority());
        flowWriter.setTempflowHardTimeout(config.getTempFlowHardTimeout());
        flowWriter.setTempflowIdleTimeout(config.getTempFlowIdleTimeout());
        flowWriter.setTempFlowPriority(config.getTempFlowPriority());
        flowWriter.setDispatchflowPriority(config.getDispatchFlowPriority());
        flowWriter.setDispatchflowHardTimeout(config.getDispatchFlowHardTimeout());
        flowWriter.setDispatchflowIdleTimeout(config.getDispatchFlowIdleTimeout());
        TopoGraph topoGraph=new TopoGraph(inventoryReader, dataBroker,delaycollectorService,bwCollectorService);
        topoGraph.updateTopo();
        PacketProcessor packetProcessor=new PacketProcessor(flowWriter, topoGraph);
        packetProcessor.setServerNode(new NodeId(config.getServerNode()));
      
        packetProcessor.setServerNodeconnector(new NodeConnectorId(config.getServerNodeConnector()));
        packetProcessor.setService(service);
        packetProcessor.setPacketProcessingService(packetProcessingService);
        DispatchPacketProcessor dispatchPacketProcessor=new DispatchPacketProcessor(dataBroker,new NodeConnectorId(config.getServerNodeConnector()),topoGraph, flowWriter);
        dispatchPacketProcessor.initMap();
        dispatchPacketProcessor.registerAsDataChangerListener();
        dispatchPacketProcessor.setPacketProcessingService(packetProcessingService);
        packetProcessor.setService(service);
        packetProcessor.setDispatchPacketProcessor(dispatchPacketProcessor);
        listenerRegistration=notificationService.registerNotificationListener(packetProcessor);
        listenerRegistration2=notificationService.registerNotificationListener(dispatchPacketProcessor);
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("SmartrouterProvider Closed");
        listenerRegistration.close();
        listenerRegistration2.close();
    }
}