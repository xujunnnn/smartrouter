/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.smartrouter.impl.packet;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.KnownIpProtocols;

public class Socket {
	private KnownIpProtocols protocol;
	private MacAddress srcMac;
	private MacAddress destMac;
	private Ipv4Address srcAddress;
	private Ipv4Address destAddress;
	private int srcPort;
	private int destPort;
	public KnownIpProtocols getProtocol() {
		return protocol;
	}
	public Socket setProtocol(KnownIpProtocols protocol) {
		this.protocol = protocol;
		return this;
	}
	public MacAddress getSrcMac() {
		return srcMac;
	}
	public Socket setSrcMac(MacAddress srcMac) {
		this.srcMac = srcMac;
		return this;
	}
	public MacAddress getDestMac() {
		return destMac;
	}
	public Socket setDestMac(MacAddress destMac) {
		this.destMac = destMac;
		return this;
	}
	public Ipv4Address getSrcAddress() {
		return srcAddress;
	}
	public Socket setSrcAddress(Ipv4Address srcAddress) {
		this.srcAddress = srcAddress;
		return this;
	}
	public Ipv4Address getDestAddress() {
		return destAddress;
	}
	public Socket setDestAddress(Ipv4Address destAddress) {
		this.destAddress = destAddress;
		return this;
	}
	public int getSrcPort() {
		return srcPort;
	}
	public Socket setSrcPort(int srcPort) {
		this.srcPort = srcPort;
		return this;
	}
	public int getDestPort() {
		return destPort;
	}
	public Socket setDestPort(int destPort) {
		this.destPort = destPort;
		return this;
	}
	/**
	 * reverse the socket
	 * @return
	 */
	public Socket reverse(){
		Socket socket=new Socket();
		socket.setSrcMac(this.destMac)
			  .setDestMac(this.srcMac)
			  .setSrcAddress(this.destAddress)
			  .setDestAddress(this.srcAddress)
			  .setProtocol(this.protocol)
			  .setSrcPort(this.destPort)
			  .setDestPort(this.srcPort);
		return socket;
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((destAddress == null) ? 0 : destAddress.hashCode());
		result = prime * result + ((destMac == null) ? 0 : destMac.hashCode());
		result = prime * result + destPort;
		result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());
		result = prime * result + ((srcAddress == null) ? 0 : srcAddress.hashCode());
		result = prime * result + ((srcMac == null) ? 0 : srcMac.hashCode());
		result = prime * result + srcPort;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Socket other = (Socket) obj;
		if (destAddress == null) {
			if (other.destAddress != null)
				return false;
		} else if (!destAddress.equals(other.destAddress))
			return false;
		if (destMac == null) {
			if (other.destMac != null)
				return false;
		} else if (!destMac.equals(other.destMac))
			return false;
		if (destPort != other.destPort)
			return false;
		if (protocol != other.protocol)
			return false;
		if (srcAddress == null) {
			if (other.srcAddress != null)
				return false;
		} else if (!srcAddress.equals(other.srcAddress))
			return false;
		if (srcMac == null) {
			if (other.srcMac != null)
				return false;
		} else if (!srcMac.equals(other.srcMac))
			return false;
		if (srcPort != other.srcPort)
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "Socket [protocol=" + protocol + ", srcMac=" + srcMac + ", destMac=" + destMac + ", srcAddress="
				+ srcAddress + ", destAddress=" + destAddress + ", srcPort=" + srcPort + ", destPort=" + destPort + "]";
	}
	
	
	

}
