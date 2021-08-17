package com.pdz.team;

import com.pdz.team.dto.*;
import com.pdz.team.dto.TeamServiceGrpc.TeamServiceStub;
import com.pdz.team.dto.TeamServiceGrpc.TeamServiceBlockingStub;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TeamClient {
    private static final Logger logger = Logger.getLogger(TeamClient.class.getName());

    private final TeamServiceBlockingStub blockingStub;
    private final TeamServiceStub asyStub;

    private Random random = new Random();

    public TeamClient(Channel channel) {
        blockingStub = TeamServiceGrpc.newBlockingStub(channel);
        asyStub = TeamServiceGrpc.newStub(channel);
    }

    public void sayHello(Member request) {
        logger.info("\n\nInit request to say hello to member: " + request.getPerson().getName());

        MessageResponse response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e){
            logger.log(Level.WARNING, "Some error occurs in the request: sayHello", e.getStatus());
            return;
        }
        logger.info("The response: " + response.getMessage());
    }

    public void getTeamByPerson(Person request) {
        logger.info("\n\nInit request to get team by person: " + request.getName());

        Team response;
        try {
            response = blockingStub.getTeamByPerson(request);
        } catch (StatusRuntimeException e){
            logger.log(Level.WARNING, "Some error occurs in the request: sayHello", e.getStatus());
            return;
        }
        logger.info("The team of: " + request.getName() + " is " + response.getName());
    }

    public void getMembersByPosition(Position request) {
        logger.info("\n\nInit request to get all members of a specific position.");

        Iterator<Member> members;
        try {
            members = blockingStub.getMembersByPosition(request);
            logger.info("The members that are " + request.getName() + ":");
            for(int i = 1; members.hasNext(); i++) {
                Member member = members.next();
                logger.info(i + ": " + member.getPerson().getName());
            }
        } catch(StatusRuntimeException e) {
            logger.log(Level.WARNING, "Some error occurs in the request: sayHello", e.getStatus());
            return;
        }
    }

    public void estimatePositionsByPersons(List<Person> request) throws InterruptedException {
        logger.info("\n\nInit request to get estimate of position by persons.");
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<EstimatePosition> responseObserver = new StreamObserver<EstimatePosition>() {
            @Override
            public void onNext(EstimatePosition estimatePosition) {
                logger.info("ProcessingInfo, current: " + estimatePosition.toString());
            }

            @Override
            public void onError(Throwable throwable) {
                warning("RecordRoute Failed: {0}", Status.fromThrowable(throwable));
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                info("Finished RecordRoute");
                finishLatch.countDown();
            }
        };

        StreamObserver<Person> requestObserver = asyStub.estimatePositionsByPersons(responseObserver);
        try {
            for(Person person : request) {
                requestObserver.onNext(person);
                if (finishLatch.getCount() == 0) {
                    return;
                }
            }
        } catch(RuntimeException e) {
            responseObserver.onError(e);
            throw e;
        }
        requestObserver.onCompleted();
        if(!finishLatch.await(1, TimeUnit.MINUTES)) {
            warning("recordRoute can not finish within 1 minutes");
        }
    }

    public CountDownLatch getPersonByTeam() {
        info("\n\n Getting persons by team request.");
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<Person> responseObserver = new StreamObserver<Person>() {
            @Override
            public void onNext(Person person) {
                info("The person: " + person);
            }

            @Override
            public void onError(Throwable throwable) {
                warning("GetPersonByTeam Failed: {0}", Status.fromThrowable(throwable));
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                info("Fnished getPersonByTeam");
                finishLatch.countDown();
            }
        };

        StreamObserver<Team> requestObserver = asyStub.getPersonByTeam(responseObserver);

        try {
            Team convivenciaTeam = Team.newBuilder().setId(1).build();
            Team crossTeam = Team.newBuilder().setId(2).build();

           Team[] requests = {convivenciaTeam, crossTeam};
           for (Team request : requests) {
               info("Sending a request:" + request);
               requestObserver.onNext(request);
           }
        } catch (RuntimeException ex) {
            requestObserver.onError(ex);
            throw ex;
        }

        requestObserver.onCompleted();

        return finishLatch;
    }

    public static void main(String[] args) throws InterruptedException {
        String target = "localhost:8980";
        if (args.length > 0) {
            if ("--help".equals(args[0])) {
                System.err.println("Usage: [target]");
                System.err.println("");
                System.err.println("  target  The server to connect to. Defaults to " + target);
                System.exit(1);
            }
            target = args[0];
        }

        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        try {
            TeamClient client = new TeamClient(channel);

            // - Hello World
            client.sayHello(getRafinhaMember());

            // - Simple RPC
            client.getTeamByPerson(getTataPerson());

            // - Server-side streaming RPC
            client.getMembersByPosition(getDeveloperPosition());

            // - Client-side streaming RPC
            client.estimatePositionsByPersons(getPersonsToEstimatePosition());

            // - Bidirectional stream RPC
            CountDownLatch finiLatch = client.getPersonByTeam();

            if(!finiLatch.await(1, TimeUnit.MINUTES)) {
                client.warning("GetPersonByTeam can not finish within 1 minutes.");
            }

        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Member getRafinhaMember() {
        return Member.newBuilder().setPerson(getRafinhaPerson())
                .setPosition(Position.newBuilder().setName("Desenvolvedor").build()).build();
    }

    private static Person getRafinhaPerson() {
        return Person.newBuilder().setId(1).setName("Rafinha").build();
    }

    private static Person getTataPerson() {
        return Person.newBuilder().setId(11).setName("Tatá").build();
    }

    private static Position getDeveloperPosition() {
        return Position.newBuilder().setId(1).setName("desenvolvedor").build();
    }

    private static List<Person> getPersonsToEstimatePosition() {
        List<Person> personList = new ArrayList<>();
        personList.add(Person.newBuilder().setId(11).setName("Tatá").build());
        personList.add(Person.newBuilder().setId(1).setName("Rafa").build());
        personList.add(Person.newBuilder().setId(10).setName("Gersão").build());
        return personList;
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }

    private void warning(String msg, Object... params) {
        logger.log(Level.WARNING, msg, params);
    }

}
