package com.yang;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

public class HttpProxyServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final String AUTHORIZATION = "Authorization";
    private static final String AUTHORIZATION_TYPE = "Bearer ";
    private static final String CHAT_GPT_NEXT_URL = "gpt-web";
    private static final int CHAT_GPT_NEXT_PORT = 3000;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    private Encoding enc;
    private OpenAIRequest openAIRequest;
    private int responseTokenNums = 0;
    private int requestPromptTokenNums = 0;
    private String token;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        request.retain();//极其重要，因为request在pipeline中会被释放，所以这里要retain一下，b.connect中处理的内容都是异步的
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        //ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpContentDecompressor());
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<HttpObject>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx2, HttpObject msg) {
                                //System.out.println("inner channel2 id: " + ctx2.channel().id());
                                //System.out.println("response channel id " + ctx.channel().id());
                                if (msg instanceof HttpContent ) {
                                    HttpContent content = (HttpContent) msg;
                                    content.retain();
                                    if (openAIRequest != null) {
                                        ByteBuf buf = content.content();
                                        String contentStr = buf.toString(StandardCharsets.UTF_8);
                                        //System.out.println("content: " + contentStr);
                                        if (openAIRequest.isGPT4Model()) {
                                            int chunkTokenNums = enc.countTokens(contentStr);
                                            if (chunkTokenNums > 70) {
                                                responseTokenNums += 3;
                                            }
                                            //System.out.println("chunkTokenNums token number: " + chunkTokenNums);
                                        }
                                    }
                                }
                                ctx.channel().writeAndFlush(msg);//use proxy channel to send response to client
                            }
                        });
                    }
                });

        String authorization = request.headers().get(AUTHORIZATION);
        if (authorization != null) {
            token = authorization.substring(AUTHORIZATION_TYPE.length());
            ByteBuf content = request.content();
            String payload = content.toString(CharsetUtil.UTF_8);
            if (!StringUtil.isNullOrEmpty(payload)) {
                try {
                    openAIRequest = objectMapper.readValue(payload, OpenAIRequest.class);
                    if (null == enc) {
                        enc = registry.getEncodingForModel(openAIRequest.getModel()).get();
                        //System.out.println("request: " + openAIRequest.getMessages().toString());
                        if (openAIRequest.isGPT4Model()) {
                            int requestPromptTokenNums = enc.countTokens(openAIRequest.getMessages().toString());
                            this.requestPromptTokenNums += requestPromptTokenNums;
                        }
                        //System.out.println("request prompt token number: " + requestPromptTokenNums);
                    }
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        }
        //check token number for GPT-4 model
        boolean shouldForward = true;
        if (null != openAIRequest && openAIRequest.isGPT4Model() && null != token) {
            String value = RedisUtil.getConnection().get(token);
            if (value != null) {
                String limit = RedisUtil.getConnection().get(token + ":limit");
                int limitNums = Integer.MAX_VALUE;
                if (limit != null) {
                    limitNums = Integer.parseInt(limit);
                }
                int tokenNums = Integer.parseInt(value);
                if (tokenNums > limitNums) {
                    ctx.channel().writeAndFlush(MockHttpResponseUtil.mockHttpResponse("当前Key(sk-..." + token.substring(token.length() - 4) + ")GPT-4模型使用数(" + tokenNums + " tokens)已经超出限制(" + limitNums + " tokens), 请使用GPT-3.5模型"));
                    shouldForward = false;
                }
            }
        }
        //System.out.println("request URL: " + request.uri());
        if (shouldForward) {
            b.connect(CHAT_GPT_NEXT_URL, CHAT_GPT_NEXT_PORT).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    //System.out.println("request URL: " + request.uri());
                    //System.out.println("inner channel1 id: " + future.channel().id());
                    future.channel().writeAndFlush(request);//use client channel to send request from proxy server to remote server
                } else {
                    future.channel().close();
                }
            });
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        //send used token number to redis
        //System.out.println("channel inactive id: " + ctx.channel().id());
        if (null != token && openAIRequest != null && openAIRequest.isGPT4Model()) {
            RedisUtil.getConnection().incrBy(token, requestPromptTokenNums + responseTokenNums);
        }
        //System.out.println("request prompt token number: " + requestPromptTokenNums);
        //System.out.println("response token number: " + responseTokenNums);
    }
}
