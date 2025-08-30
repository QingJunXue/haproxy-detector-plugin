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
                logger.info("HAProxy 检测器：正在处理来自 " + ctx.channel().remoteAddress() + " 的 " + in.readableBytes() + " 字节数据");
            }
            
            // 安全检查：确保有足够的数据进行检测
            if (in.readableBytes() < 16) {
                if (logger != null) {
                    logger.info("HAProxy 检测器：数据不足以进行检测（" + in.readableBytes() + " 字节），等待更多数据...");
                }
                return; // 等待更多数据
            }
            
            ProtocolDetectionResult<HAProxyProtocolVersion> detectionResult;
            try {
                detectionResult = HAProxyMessageDecoder.detectProtocol(in);
            } catch (IndexOutOfBoundsException e) {
                if (logger != null) {
                    logger.info("HAProxy 检测器：检测过程中发生缓冲区下溢，等待更多数据。错误：" + e.getMessage());
                }
                return; // 等待更多数据
            }
            
            if (logger != null) {
                logger.info("HAProxy 检测结果：" + detectionResult.state() + "，来源 " + ctx.channel().remoteAddress());
            }
            
            switch (detectionResult.state()) {
                case NEEDS_MORE_DATA:
                    if (logger != null) {
                        logger.info("HAProxy 检测器：需要更多数据，等待中...");
                    }
                    return;
                case INVALID:
                    if (logger != null) {
                        logger.info("HAProxy 检测器：协议无效，从 " + ctx.channel().remoteAddress() + " 移除检测器");
                    }
                    ctx.pipeline().remove(this);
                    break;
                case DETECTED:
                default:
                    if (logger != null) {
                        logger.info("检测到 HAProxy 协议，来源 " + ctx.channel().remoteAddress() + "，版本：" + detectionResult.detectedProtocol());
                    }
                    SocketAddress addr = ctx.channel().remoteAddress();
                    if (logger != null) {
                        logger.info("HAProxy 检测器：正在校验白名单，地址：" + addr);
                    }
                    
                    if (!ProxyWhitelist.check(addr)) {
                        if (logger != null) {
                            logger.warning("HAProxy 检测器：来源地址 " + addr + " 不在白名单，关闭连接");
                        }
                        try {
                            ProxyWhitelist.getWarningFor(addr).ifPresent(logger::info);
                        } finally {
                            ctx.close();
                        }
                        return;
                    }

                    if (logger != null) {
                        logger.info("HAProxy 检测器：白名单校验通过，开始配置管线");
                    }

                    ChannelPipeline pipeline = ctx.pipeline();
                    try {
                        pipeline.replace(this, "haproxy-decoder", new HAProxyMessageDecoder());
                        if (logger != null) {
                            logger.info("HAProxy 检测器：已用 HAProxy 解码器替换检测器");
                        }
                    } catch (IllegalArgumentException ignored) {
                        pipeline.remove(this); // decoder already exists
                        if (logger != null) {
                            logger.warning("HAProxy 检测器：解码器已存在，移除检测器");
                        }
                    }

                    if (haproxyHandler != null) {
                        try {
                            pipeline.addAfter("haproxy-decoder", "haproxy-handler", haproxyHandler);
                            if (logger != null) {
                                logger.info("HAProxy 检测器：已将 HAProxy 处理器添加至管线");
                            }
                        } catch (IllegalArgumentException ignored) {
                            if (logger != null) {
                                logger.warning("HAProxy 检测器：处理器已存在");
                            }
                        } catch (NoSuchElementException e) {  // Not sure why but...
                            if (logger != null) {
                                logger.warning("HAProxy 检测器：未找到解码器，尝试备用放置位置");
                            }
                            if (pipeline.get("timeout") != null) {
                                pipeline.addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
                                pipeline.addAfter("timeout", "haproxy-handler", haproxyHandler);
                                if (logger != null) {
                                    logger.info("HAProxy 检测器：已在 timeout 处理器之后添加组件");
                                }
                            } else {
                                pipeline.addFirst("haproxy-handler", haproxyHandler);
                                pipeline.addFirst("haproxy-decoder", new HAProxyMessageDecoder());
                                if (logger != null) {
                                    logger.info("HAProxy 检测器：已在管线开头添加组件");
                                }
                            }
                        }
                    } else {
                        if (logger != null) {
                            logger.info("HAProxy 检测器：未提供 HAProxy 处理器");
                        }
                    }
                    break;
            }
        }  catch (Throwable t) {  // stop BC from eating my exceptions
            if (logger != null)
                logger.log(Level.WARNING, "检测代理时发生异常", t);
            else 
                t.printStackTrace();
        }
    }
}
