//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.http2.server;

import java.util.concurrent.Executor;

import org.eclipse.jetty.http2.HTTP2Connection;
import org.eclipse.jetty.http2.HTTP2FlowControl;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.generator.Generator;
import org.eclipse.jetty.http2.parser.ErrorCode;
import org.eclipse.jetty.http2.parser.Parser;
import org.eclipse.jetty.http2.parser.ServerParser;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.AbstractConnectionFactory;
import org.eclipse.jetty.server.Connector;

public abstract class AbstractHTTP2ServerConnectionFactory extends AbstractConnectionFactory
{
    private int maxHeaderTableSize = 4096;
    private int initialWindowSize = 65535;
    private int maxConcurrentStreams = -1;

    public AbstractHTTP2ServerConnectionFactory()
    {
        super("h2-12");
    }

    public int getMaxHeaderTableSize()
    {
        return maxHeaderTableSize;
    }

    public void setMaxHeaderTableSize(int maxHeaderTableSize)
    {
        this.maxHeaderTableSize = maxHeaderTableSize;
    }

    public int getInitialWindowSize()
    {
        return initialWindowSize;
    }

    public void setInitialWindowSize(int initialWindowSize)
    {
        this.initialWindowSize = initialWindowSize;
    }

    public int getMaxConcurrentStreams()
    {
        return maxConcurrentStreams;
    }

    public void setMaxConcurrentStreams(int maxConcurrentStreams)
    {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        ServerSessionListener listener = newSessionListener(connector, endPoint);

        Generator generator = new Generator(connector.getByteBufferPool(), getMaxHeaderTableSize());
        HTTP2ServerSession session = new HTTP2ServerSession(endPoint, generator, listener,
                new HTTP2FlowControl(getInitialWindowSize()), getMaxConcurrentStreams());

        Parser parser = newServerParser(connector.getByteBufferPool(), session);
        HTTP2Connection connection = new HTTP2ServerConnection(connector.getByteBufferPool(), connector.getExecutor(),
                        endPoint, parser, getInputBufferSize(), listener, session);

        return configure(connection, connector, endPoint);
    }

    protected abstract ServerSessionListener newSessionListener(Connector connector, EndPoint endPoint);

    protected abstract ServerParser newServerParser(ByteBufferPool byteBufferPool, ServerParser.Listener listener);

    private class HTTP2ServerConnection extends HTTP2Connection
    {
        private final ServerSessionListener listener;
        private final Session session;

        public HTTP2ServerConnection(ByteBufferPool byteBufferPool, Executor executor, EndPoint endPoint, Parser parser, int inputBufferSize, ServerSessionListener listener, Session session)
        {
            super(byteBufferPool, executor, endPoint, parser, inputBufferSize);
            this.listener = listener;
            this.session = session;
        }

        @Override
        public void onOpen()
        {
            super.onOpen();
            notifyConnect(session);
        }

        @Override
        protected boolean onReadTimeout()
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Idle timeout {}ms expired on {}", getEndPoint().getIdleTimeout(), this);
            session.close(ErrorCode.NO_ERROR, "idle_timeout", closeCallback);
            return false;
        }

        private void notifyConnect(Session session)
        {
            try
            {
                listener.onConnect(session);
            }
            catch (Throwable x)
            {
                LOG.info("Failure while notifying listener " + listener, x);
            }
        }
    }
}
