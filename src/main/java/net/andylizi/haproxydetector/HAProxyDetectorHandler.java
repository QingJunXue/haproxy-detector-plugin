/*
 * Copyright (C) 2020 Andy Li
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Lesser Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
*/
package net.andylizi.haproxydetector;

import java.net.SocketAddress;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;

public class HAProxyDetectorHandler extends ByteToMessageDecoder {
    private final Logger logger;
    private final ChannelHandler haproxyHandler;

    {
        setSingleDecode(true);
    }

    public HAProxyDetectorHandler(Logger logger, ChannelHandler haproxyHandler) {
        this.logger = logger;
        this.haproxyHandler = haproxyHandler;
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        try {
            if (logger != null) {
                logger.info("HAProxy detector: Processing " + in.readableBytes() + " bytes from " + ctx.channel().remoteAddress());
            }
            
            // 安全检查：确保有足够的数据进行检测
            if (in.readableBytes() < 16) {
                if (logger != null) {
                    logger.info("HAProxy detector: Not enough data for detection (" + in.readableBytes() + " bytes), waiting...");
                }
                return; // 等待更多数据
            }
            
            ProtocolDetectionResult<HAProxyProtocolVersion> detectionResult;
            try {
                detectionResult = HAProxyMessageDecoder.detectProtocol(in);
            } catch (IndexOutOfBoundsException e) {
                if (logger != null) {
                    logger.info("HAProxy detector: Buffer underflow during detection, waiting for more data. Error: " + e.getMessage());
                }
                return; // 等待更多数据
            }
            
            if (logger != null) {
                logger.info("HAProxy detection result: " + detectionResult.state() + " from " + ctx.channel().remoteAddress());
            }
            
            switch (detectionResult.state()) {
                case NEEDS_MORE_DATA:
                    if (logger != null) {
                        logger.info("HAProxy detector: Need more data, waiting...");
                    }
                    return;
                case INVALID:
                    if (logger != null) {
                        logger.info("HAProxy detector: Invalid protocol, removing detector from " + ctx.channel().remoteAddress());
                    }
                    ctx.pipeline().remove(this);
                    break;
                case DETECTED:
                default:
                    if (logger != null) {
                        logger.info("HAProxy protocol detected from " + ctx.channel().remoteAddress() + ", version: " + detectionResult.detectedProtocol());
                    }
                    SocketAddress addr = ctx.channel().remoteAddress();
                    if (logger != null) {
                        logger.info("HAProxy detector: Checking whitelist for address: " + addr);
                    }
                    
                    if (!ProxyWhitelist.check(addr)) {
                        if (logger != null) {
                            logger.warning("HAProxy detector: Address " + addr + " not in whitelist, closing connection");
                        }
                        try {
                            ProxyWhitelist.getWarningFor(addr).ifPresent(logger::info);
                        } finally {
                            ctx.close();
                        }
                        return;
                    }

                    if (logger != null) {
                        logger.info("HAProxy detector: Address whitelist check passed, setting up pipeline");
                    }

                    ChannelPipeline pipeline = ctx.pipeline();
                    try {
                        pipeline.replace(this, "haproxy-decoder", new HAProxyMessageDecoder());
                        if (logger != null) {
                            logger.info("HAProxy detector: Successfully replaced detector with HAProxy decoder");
                        }
                    } catch (IllegalArgumentException ignored) {
                        pipeline.remove(this); // decoder already exists
                        if (logger != null) {
                            logger.warning("HAProxy detector: Decoder already exists, removing detector");
                        }
                    }

                    if (haproxyHandler != null) {
                        try {
                            pipeline.addAfter("haproxy-decoder", "haproxy-handler", haproxyHandler);
                            if (logger != null) {
                                logger.info("HAProxy detector: Successfully added HAProxy handler to pipeline");
                            }
                        } catch (IllegalArgumentException ignored) {
                            if (logger != null) {
                                logger.warning("HAProxy detector: Handler already exists");
                            }
                        } catch (NoSuchElementException e) {  // Not sure why but...
                            if (logger != null) {
                                logger.warning("HAProxy detector: Decoder not found, trying alternative placement");
                            }
                            if (pipeline.get("timeout") != null) {
                                pipeline.addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
                                pipeline.addAfter("timeout", "haproxy-handler", haproxyHandler);
                                if (logger != null) {
                                    logger.info("HAProxy detector: Added components after timeout handler");
                                }
                            } else {
                                pipeline.addFirst("haproxy-handler", haproxyHandler);
                                pipeline.addFirst("haproxy-decoder", new HAProxyMessageDecoder());
                                if (logger != null) {
                                    logger.info("HAProxy detector: Added components at beginning of pipeline");
                                }
                            }
                        }
                    } else {
                        if (logger != null) {
                            logger.info("HAProxy detector: No HAProxy handler provided");
                        }
                    }
                    break;
            }
        }  catch (Throwable t) {  // stop BC from eating my exceptions
            if (logger != null)
                logger.log(Level.WARNING, "Exception while detecting proxy", t);
            else 
                t.printStackTrace();
        }
    }
}
