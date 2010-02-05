package org.modeshape.jcr;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.stub;
import java.util.LinkedList;
import java.util.UUID;
import javax.jcr.RepositoryException;
import org.modeshape.graph.ExecutionContext;
import org.modeshape.graph.Graph;
import org.modeshape.graph.Location;
import org.modeshape.graph.connector.MockRepositoryConnection;
import org.modeshape.graph.connector.RepositoryConnectionFactory;
import org.modeshape.graph.property.Name;
import org.modeshape.graph.property.Path;
import org.modeshape.graph.property.PathFactory;
import org.modeshape.graph.property.Property;
import org.modeshape.graph.property.PropertyFactory;
import org.modeshape.graph.request.LockBranchRequest;
import org.modeshape.graph.request.Request;
import org.modeshape.graph.request.UnlockBranchRequest;
import org.modeshape.graph.request.LockBranchRequest.LockScope;
import org.modeshape.jcr.WorkspaceLockManager.ModeShapeLock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoAnnotations.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class WorkspaceLockManagerTest {

    protected Graph graph;
    private ExecutionContext context;
    private UUID validUuid;
    private Location validLocation;
    private String sourceName;
    private String workspaceName;
    private MockRepositoryConnection connection;
    private LinkedList<Request> executedRequests;

    private RepositoryNodeTypeManager repoTypeManager;
    protected WorkspaceLockManager workspaceLockManager;

    @Mock
    private RepositoryConnectionFactory connectionFactory;
    @Mock
    protected JcrRepository repository;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        executedRequests = new LinkedList<Request>();
        sourceName = "Source";
        workspaceName = "default";
        context = new ExecutionContext();
        connection = new MockRepositoryConnection(sourceName, executedRequests);
        stub(connectionFactory.createConnection(sourceName)).toReturn(connection);
        graph = Graph.create(sourceName, connectionFactory, context);

        validUuid = UUID.randomUUID();
        validLocation = Location.create(validUuid);


        PathFactory pathFactory = context.getValueFactories().getPathFactory();
        stub(repository.getExecutionContext()).toReturn(context);
        stub(repository.getRepositorySourceName()).toReturn(sourceName);
        stub(repository.getPersistentRegistry()).toReturn(context.getNamespaceRegistry());
        stub(repository.createWorkspaceGraph(anyString(), (ExecutionContext)anyObject())).toAnswer(new Answer<Graph>() {
            public Graph answer( InvocationOnMock invocation ) throws Throwable {
                return graph;
            }
        });
        stub(repository.createSystemGraph(context)).toAnswer(new Answer<Graph>() {
            public Graph answer( InvocationOnMock invocation ) throws Throwable {
                return graph;
            }
        });

        Path locksPath = pathFactory.createAbsolutePath(JcrLexicon.SYSTEM, ModeShapeLexicon.LOCKS);
        workspaceLockManager = new WorkspaceLockManager(context, repository, workspaceName, locksPath);

        stub(repository.getLockManager(anyString())).toAnswer(new Answer<WorkspaceLockManager>() {
            public WorkspaceLockManager answer( InvocationOnMock invocation ) throws Throwable {
                return workspaceLockManager;
            }
        });

        // Stub out the repository, since we only need a few methods ...
        repoTypeManager = new RepositoryNodeTypeManager(repository, true);

        stub(repository.getRepositoryTypeManager()).toReturn(repoTypeManager);

        executedRequests.clear();
    }

    protected Path createPath( String path ) {
        return context.getValueFactories().getPathFactory().create(path);
    }

    protected Path createPath( Path parent,
                               String path ) {
        return context.getValueFactories().getPathFactory().create(parent, path);
    }

    protected Name createName( String name ) {
        return context.getValueFactories().getNameFactory().create(name);
    }

    protected Property createProperty( String name,
                                       Object... values ) {
        return context.getPropertyFactory().create(createName(name), values);
    }

    protected void assertNextRequestIsLock( Location at,
                                            LockScope lockScope,
                                            long lockTimeout ) {
        Request request = executedRequests.poll();
        assertThat(request, is(instanceOf(LockBranchRequest.class)));
        LockBranchRequest lock = (LockBranchRequest)request;
        assertThat(lock.at(), is(at));
        assertThat(lock.lockScope(), is(lockScope));
        assertThat(lock.lockTimeoutInMillis(), is(lockTimeout));
    }

    protected void assertNextRequestIsUnlock( Location at ) {
        Request request = executedRequests.poll();
        assertThat(request, is(instanceOf(UnlockBranchRequest.class)));
        UnlockBranchRequest unlock = (UnlockBranchRequest)request;
        assertThat(unlock.at(), is(at));
    }

    @Test
    public void shouldCreateLockRequestWhenLockingNode() throws RepositoryException {
        ModeShapeLock lock = workspaceLockManager.createLock("testOwner", UUID.randomUUID(), validUuid, false, false);
        PropertyFactory propFactory = context.getPropertyFactory();
        String lockOwner = "testOwner";
        boolean isDeep = false;

        Property lockOwnerProp = propFactory.create(JcrLexicon.LOCK_OWNER, lockOwner);
        Property lockIsDeepProp = propFactory.create(JcrLexicon.LOCK_IS_DEEP, isDeep);

        JcrSession session = mock(JcrSession.class);
        stub(session.getExecutionContext()).toReturn(context);
        workspaceLockManager.lockNodeInRepository(session, validUuid, lockOwnerProp, lockIsDeepProp, lock, isDeep);

        assertNextRequestIsLock(validLocation, LockScope.SELF_ONLY, 0);
    }

    @Test
    public void shouldCreateLockRequestWhenUnlockingNode() {
        ModeShapeLock lock = workspaceLockManager.createLock("testOwner", UUID.randomUUID(), validUuid, false, false);
        workspaceLockManager.unlockNodeInRepository(context, lock);

        assertNextRequestIsUnlock(validLocation);
    }

}