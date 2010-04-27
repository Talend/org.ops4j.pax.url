package org.apache.maven.repository.internal;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.maven.repository.Artifact;
import org.apache.maven.repository.ArtifactMultiTransferException;
import org.apache.maven.repository.ArtifactNotFoundException;
import org.apache.maven.repository.ArtifactRequest;
import org.apache.maven.repository.ArtifactTransferException;
import org.apache.maven.repository.Authentication;
import org.apache.maven.repository.AuthenticationSelector;
import org.apache.maven.repository.ChecksumFailureException;
import org.apache.maven.repository.DefaultTransferEvent;
import org.apache.maven.repository.Metadata;
import org.apache.maven.repository.MetadataMultiTransferException;
import org.apache.maven.repository.MetadataNotFoundException;
import org.apache.maven.repository.MetadataRequest;
import org.apache.maven.repository.MetadataTransferException;
import org.apache.maven.repository.NoRepositoryReaderException;
import org.apache.maven.repository.Proxy;
import org.apache.maven.repository.ProxySelector;
import org.apache.maven.repository.RemoteRepository;
import org.apache.maven.repository.RepositoryContext;
import org.apache.maven.repository.RepositoryPolicy;
import org.apache.maven.repository.RepositoryReader;
import org.apache.maven.repository.TransferEvent;
import org.apache.maven.repository.TransferListener;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.observers.ChecksumObserver;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.util.FileUtils;

/**
 * @author Benjamin Bentmann
 */
class WagonRepositoryReader
    implements RepositoryReader
{

    private final PlexusContainer container;

    private final String wagonHint;

    private final Repository wagonRepo;

    private final AuthenticationInfo wagonAuth;

    private final ProxyInfoProvider wagonProxy;

    private final Queue<Wagon> wagons = new ConcurrentLinkedQueue<Wagon>();

    private final Maven2Layout layout = new Maven2Layout();

    private final Executor executor;

    private final TransferListener listener;

    private boolean closed;

    public WagonRepositoryReader( PlexusContainer container, RemoteRepository repository, RepositoryContext context )
        throws NoRepositoryReaderException
    {
        this.container = container;
        this.listener = context.getTransferListener();

        wagonRepo = new Repository( repository.getId(), repository.getUrl() );
        wagonHint = wagonRepo.getProtocol().toLowerCase( Locale.ENGLISH );
        wagonAuth = getAuthenticationInfo( repository, context.getAuthenticationSelector() );
        wagonProxy = getProxy( repository, context.getProxySelector() );

        try
        {
            releaseWagon( lookupWagon() );
        }
        catch ( Exception e )
        {
            throw new NoRepositoryReaderException( repository );
        }

        int threads = Integer.getInteger( "maven.artifact.threads", 5 ).intValue();
        if ( threads <= 1 )
        {
            executor = new Executor()
            {
                public void execute( Runnable command )
                {
                    command.run();
                }
            };
        }
        else
        {
            executor =
                new ThreadPoolExecutor( threads, threads, 3, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>() );
        }
    }

    private Wagon lookupWagon()
        throws Exception
    {
        return container.lookup( Wagon.class, wagonHint );
    }

    private void releaseWagon( Wagon wagon )
    {
        try
        {
            if ( wagon != null )
            {
                container.release( wagon );
            }
        }
        catch ( Exception e )
        {
            // too bad
        }
    }

    private void connectWagon( Wagon wagon )
        throws Exception
    {
        wagon.setTimeout( 10 * 1000 );
        wagon.connect( wagonRepo, wagonAuth, wagonProxy );

        // TODO: setup transfer listener, checksum observer
    }

    private void disconnectWagon( Wagon wagon )
    {
        try
        {
            if ( wagon != null )
            {
                wagon.disconnect();
            }
        }
        catch ( Exception e )
        {
            // too bad
        }
    }

    private AuthenticationInfo getAuthenticationInfo( RemoteRepository repository, AuthenticationSelector selector )
    {
        AuthenticationInfo auth = null;

        if ( selector != null )
        {
            Authentication a = selector.getAuthentication( repository );
            if ( a != null )
            {
                auth = new AuthenticationInfo();
                auth.setUserName( a.getUsername() );
                auth.setPassword( a.getPassword() );
                auth.setPrivateKey( a.getPrivateKeyFile() );
                auth.setPassphrase( a.getPassphrase() );
            }
        }

        return auth;
    }

    private ProxyInfoProvider getProxy( RemoteRepository repository, ProxySelector selector )
    {
        ProxyInfoProvider proxy = null;

        if ( selector != null )
        {
            Proxy p = selector.getProxy( repository );
            if ( p != null )
            {
                final ProxyInfo prox = new ProxyInfo();
                prox.setType( p.getType() );
                prox.setHost( p.getHost() );
                prox.setPort( p.getPort() );
                if ( p.getAuthentication() != null )
                {
                    prox.setUserName( p.getAuthentication().getUsername() );
                    prox.setPassword( p.getAuthentication().getPassword() );
                }
                proxy = new ProxyInfoProvider()
                {
                    public ProxyInfo getProxyInfo( String protocol )
                    {
                        return prox;
                    }
                };
            }
        }

        return proxy;
    }

    public void getArtifacts( Collection<? extends ArtifactRequest> requests )
        throws ArtifactMultiTransferException
    {
        if ( closed )
        {
            throw new IllegalStateException( "reader closed" );
        }

        CountDownLatch latch = new CountDownLatch( requests.size() );
        Collection<GetTask<Artifact>> tasks = new ArrayList<GetTask<Artifact>>();
        for ( ArtifactRequest request : requests )
        {
            Artifact artifact = request.getArtifact();
            String resource = layout.getPath( artifact );
            GetTask<Artifact> task =
                new GetTask<Artifact>( resource, artifact.getFile(), request.getChecksumPolicy(), latch, artifact );
            tasks.add( task );
            executor.execute( task );
        }

        Collection<ArtifactTransferException> exceptions = new ArrayList<ArtifactTransferException>();
        try
        {
            latch.await();

            for ( GetTask<Artifact> task : tasks )
            {
                Exception e = task.getException();
                if ( e instanceof ResourceDoesNotExistException )
                {
                    exceptions.add( new ArtifactNotFoundException( task.getItem() ) );
                }
                else if ( e != null )
                {
                    exceptions.add( new ArtifactTransferException( task.getItem(), e ) );
                }
            }
        }
        catch ( InterruptedException e )
        {
            for ( ArtifactRequest request : requests )
            {
                exceptions.add( new ArtifactTransferException( request.getArtifact(), e ) );
            }
        }
        if ( !exceptions.isEmpty() )
        {
            throw new ArtifactMultiTransferException( exceptions );
        }
    }

    public void getMetadata( Collection<? extends MetadataRequest> requests )
        throws MetadataMultiTransferException
    {
        if ( closed )
        {
            throw new IllegalStateException( "reader closed" );
        }

        CountDownLatch latch = new CountDownLatch( requests.size() );
        Collection<GetTask<Metadata>> tasks = new ArrayList<GetTask<Metadata>>();
        for ( MetadataRequest request : requests )
        {
            Metadata metadata = request.getMetadata();
            String resource = layout.getPath( metadata );
            GetTask<Metadata> task =
                new GetTask<Metadata>( resource, metadata.getFile(), request.getChecksumPolicy(), latch, metadata );
            tasks.add( task );
            executor.execute( task );
        }

        Collection<MetadataTransferException> exceptions = new ArrayList<MetadataTransferException>();
        try
        {
            latch.await();

            for ( GetTask<Metadata> task : tasks )
            {
                Exception e = task.getException();
                if ( e instanceof ResourceDoesNotExistException )
                {
                    exceptions.add( new MetadataNotFoundException( task.getItem() ) );
                }
                else if ( e != null )
                {
                    exceptions.add( new MetadataTransferException( task.getItem(), e ) );
                }
            }
        }
        catch ( InterruptedException e )
        {
            for ( MetadataRequest request : requests )
            {
                exceptions.add( new MetadataTransferException( request.getMetadata(), e ) );
            }
        }
        if ( !exceptions.isEmpty() )
        {
            throw new MetadataMultiTransferException( exceptions );
        }
    }

    public void close()
    {
        closed = true;

        if ( executor instanceof ExecutorService )
        {
            ( (ExecutorService) executor ).shutdown();
        }

        for ( Wagon wagon = wagons.poll(); wagon != null; wagon = wagons.poll() )
        {
            disconnectWagon( wagon );
            releaseWagon( wagon );
        }
    }

    class GetTask<T>
        implements Runnable
    {

        private final T item;

        private final String path;

        private final File file;

        private final String checksumPolicy;

        private final CountDownLatch latch;

        private volatile Exception exception;

        public GetTask( String path, File file, String checksumPolicy, CountDownLatch latch, T item )
        {
            this.path = path;
            this.file = file;
            this.checksumPolicy = checksumPolicy;
            this.latch = latch;
            this.item = item;
        }

        public T getItem()
        {
            return item;
        }

        public Exception getException()
        {
            return exception;
        }

        public void run()
        {
            WagonTransferListenerAdapter wagonListener = null;
            if ( listener != null )
            {
                wagonListener = new WagonTransferListenerAdapter( listener, wagonRepo.getUrl(), path );
            }

            try
            {
                if ( listener != null )
                {
                    DefaultTransferEvent event = new DefaultTransferEvent();
                    event.setResource( wagonListener.getResource() );
                    event.setRequestType( TransferEvent.RequestType.GET );
                    event.setType( TransferEvent.EventType.INITIATED );
                    listener.transferInitiated( event );
                }

                File tmp = new File( file.getPath() + ".tmp" );

                Wagon wagon = wagons.poll();
                if ( wagon == null )
                {
                    try
                    {
                        wagon = lookupWagon();
                        connectWagon( wagon );
                    }
                    catch ( Exception e )
                    {
                        releaseWagon( wagon );
                        throw e;
                    }
                }

                try
                {
                    for ( int trial = 1; trial >= 0; trial-- )
                    {
                        ChecksumObserver sha1 = new ChecksumObserver( "SHA-1" );
                        ChecksumObserver md5 = new ChecksumObserver( "MD5" );
                        try
                        {
                            wagon.addTransferListener( wagonListener );
                            wagon.addTransferListener( md5 );
                            wagon.addTransferListener( sha1 );

                            wagon.get( path, tmp );
                        }
                        finally
                        {
                            wagon.removeTransferListener( wagonListener );
                            wagon.removeTransferListener( md5 );
                            wagon.removeTransferListener( sha1 );
                        }

                        if ( !RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( checksumPolicy ) )
                        {
                            try
                            {
                                if ( !verifyChecksum( wagon, sha1.getActualChecksum(), ".sha1" )
                                    && !verifyChecksum( wagon, md5.getActualChecksum(), ".md5" ) )
                                {
                                    trial = 0;
                                    throw new ChecksumFailureException( "Checksum validation failed"
                                        + ", no checksums available from the repository" );
                                }
                                break;
                            }
                            catch ( ChecksumFailureException e )
                            {
                                if ( trial <= 0 && RepositoryPolicy.CHECKSUM_POLICY_FAIL.equals( checksumPolicy ) )
                                {
                                    throw e;
                                }
                                if ( listener != null )
                                {
                                    DefaultTransferEvent event = new DefaultTransferEvent();
                                    event.setResource( wagonListener.getResource() );
                                    event.setRequestType( TransferEvent.RequestType.GET );
                                    event.setType( TransferEvent.EventType.CORRUPTED );
                                    event.setException( e );
                                    listener.transferCorrupted( event );
                                }
                            }
                        }
                    }

                    rename( tmp, file );

                    if ( listener != null )
                    {
                        DefaultTransferEvent event = new DefaultTransferEvent();
                        event.setResource( wagonListener.getResource() );
                        event.setRequestType( TransferEvent.RequestType.GET );
                        event.setType( TransferEvent.EventType.SUCCEEDED );
                        listener.transferSucceeded( event );
                    }
                }
                finally
                {
                    tmp.delete();
                    wagons.add( wagon );
                }
            }
            catch ( Exception e )
            {
                exception = e;

                if ( listener != null )
                {
                    DefaultTransferEvent event = new DefaultTransferEvent();
                    event.setResource( wagonListener.getResource() );
                    event.setRequestType( TransferEvent.RequestType.GET );
                    event.setType( TransferEvent.EventType.FAILED );
                    event.setException( e );
                    listener.transferFailed( event );
                }
            }
            finally
            {
                latch.countDown();
            }
        }

        private boolean verifyChecksum( Wagon wagon, String actual, String ext )
            throws ChecksumFailureException
        {
            File tmp = new File( file.getPath() + ext + ".tmp" );

            try
            {
                try
                {
                    wagon.get( path + ext, tmp );
                }
                catch ( ResourceDoesNotExistException e )
                {
                    return false;
                }
                catch ( WagonException e )
                {
                    throw new ChecksumFailureException( e );
                }

                String expected;

                try
                {
                    expected = ChecksumUtils.read( tmp );
                }
                catch ( IOException e )
                {
                    throw new ChecksumFailureException( e );
                }

                if ( expected.equalsIgnoreCase( actual ) )
                {
                    try
                    {
                        rename( tmp, new File( file.getPath() + ext ) );
                    }
                    catch ( IOException e )
                    {
                        // ignored, non-critical
                    }
                }
                else
                {
                    throw new ChecksumFailureException( expected, actual );
                }
            }
            finally
            {
                tmp.delete();
            }

            return true;
        }

        private void rename( File from, File to )
            throws IOException
        {
            if ( !from.renameTo( file ) )
            {
                FileUtils.copyFile( from, file );
            }
        }

    }

}
