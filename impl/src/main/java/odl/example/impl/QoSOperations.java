/*
 * Copyright © 2017 M.E Xezonaki in the context of her MSc Thesis, Department of Informatics and Telecommunications, UoA.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package odl.example.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Counter32;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.FlowCapableNodeConnectorStatisticsData;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The class measuring the QoS of the topology.
 *
 * @author Marievi Xezonaki
 */
public class QoSOperations {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleProvider.class);
    private DataBroker db;
    private String pathInputPort, pathOutputPort;
    private static HashMap<String, BigInteger> packetsTransmittedList = new HashMap<>();
    private static HashMap<String, BigInteger> packetsReceivedList = new HashMap<>();
    private List<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> nodeList = null;

    public QoSOperations(DataBroker dataBroker, String pathInputPort, String pathOutputPort){
        this.db = dataBroker;
        this.pathInputPort = pathInputPort;
        this.pathOutputPort = pathOutputPort;
    }

    /**
     * The method which monitors the packet loss and delay of all links in the topology.
     *
     * * @return  It returns a list of links with their delay and packet loss.
     */
    public List<LinkWithQoS> getAllLinksWithQos() {

        try {
            nodeList = getNodes(db);
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
        if (nodeList != null) {
            List<LinkWithQoS> linksToReturn = new ArrayList<>();
            List<Link> links = getAllLinks();
            if (links != null) {
                for (Link link : links) {
                    String nodeToFind = link.getSource().getSourceNode().getValue();
                    String port = link.getSource().getSourceTp().getValue();

                    for (org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node node : nodeList) {

                        if (node.getId().getValue().equals(nodeToFind)) {

                            List<NodeConnector> nodeConnectors = node.getNodeConnector();

                            for (NodeConnector nc : nodeConnectors) {

                                if (nc.getId().getValue().equals(port)) {
                                    FlowCapableNodeConnectorStatisticsData statData = nc.getAugmentation(FlowCapableNodeConnectorStatisticsData.class);
                                    org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.flow.capable.node.connector.statistics.FlowCapableNodeConnectorStatistics statistics = statData.getFlowCapableNodeConnectorStatistics();
                                    BigInteger packetsTransmitted = statistics.getPackets().getTransmitted();
                                    BigInteger packetErrorsTransmitted = statistics.getTransmitErrors();
                                    Float packetLoss = (packetsTransmitted.floatValue() == 0) ? 0 : packetErrorsTransmitted.floatValue() / packetsTransmitted.floatValue();
                                    BigInteger packetsReceived = statistics.getPackets().getReceived();

                                    linksToReturn.add(new LinkWithQoS(packetLoss.longValue(), -1L, link));
                                    if (port.equals(pathInputPort) || port.equals(pathOutputPort)) {
                                        BigInteger packetsTransmittedThisIteration, packetsReceivedThisIteration;
                                        if (packetsTransmittedList.containsKey(port) && (packetsReceivedList.containsKey(port))) {
                                            packetsTransmittedThisIteration = packetsTransmitted.subtract(packetsTransmittedList.get(port));
                                            packetsReceivedThisIteration = packetsReceived.subtract(packetsReceivedList.get(port));
                                        } else {
                                            packetsTransmittedThisIteration = packetsTransmitted;
                                            packetsReceivedThisIteration = packetsReceived;
                                        }
                                        System.out.println("For " + port + " , packets transmitted are : " + packetsTransmittedThisIteration + " , packets received are : " + packetsReceivedThisIteration);
                                    }

                                    //fill the maps
                                    packetsTransmittedList.put(port, packetsTransmitted);
                                    packetsReceivedList.put(port, packetsReceived);
                                }
                            }
                        }
                    }
                }
                return linksToReturn;
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * The method which finds all links of the topology from the datastore.
     *
     * * @return  It returns a list of all links.
     */
    public List<Link> getAllLinks() {
        List<Link> linkList = new ArrayList<>();

        try {
            TopologyId topoId = new TopologyId("flow:1");
            InstanceIdentifier<Topology> nodesIid = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(topoId)).toInstance();
            ReadOnlyTransaction nodesTransaction = db.newReadOnlyTransaction();
            CheckedFuture<Optional<Topology>, ReadFailedException> nodesFuture = nodesTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
            Optional<Topology> nodesOptional = nodesFuture.checkedGet();
            if (nodesOptional != null && nodesOptional.isPresent())
                linkList = nodesOptional.get().getLink();
            return linkList;
        } catch (Exception e) {
            LOG.info("Node Fetching Failed");
            return linkList;
        }

    }

    /**
     * The method which finds all nodes of the topology from the datastore.
     *
     * * @return  It returns a list of all nodes.
     */
    public static List<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> getNodes(DataBroker db) {

        List<org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node> nodeList = new ArrayList<>();
        InstanceIdentifier<Nodes> nodesIid = InstanceIdentifier.builder(
                Nodes.class).build();
        ReadOnlyTransaction nodesTransaction = db.newReadOnlyTransaction();
        CheckedFuture<Optional<Nodes>, ReadFailedException> nodesFuture = nodesTransaction
                .read(LogicalDatastoreType.OPERATIONAL, nodesIid);
        Optional<Nodes> nodesOptional = null;
        try {
            nodesOptional = nodesFuture.checkedGet();
        } catch (ReadFailedException e) {
            e.printStackTrace();
        }
        if (nodesOptional != null && nodesOptional.isPresent()) {
            nodeList = nodesOptional.get().getNode();
        }

        return nodeList;
    }

    /**
     * The method which estimates the current QoE based on the ITU-T E-model.
     *
     * * @return  It returns the MOS value estimation.
     */
    public double QoEEstimation(Long packetLoss, Long delay){
        int h;
        if (delay - 177.3 > 0){
            h = 1;
        }
        else {
            h = 0;
        }
        double R = 94.2 - 0.024*delay - 0.11*h*(delay-177.3) - 11 - 40*Math.log(1+10*packetLoss);
        double MOS;
        if (R < 0){
            MOS = 0;
        }
        else{
            MOS = 1 + 0.035*R + R*(R-60)*(100-R)/1000000;
        }
        return MOS;
    }
}
