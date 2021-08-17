package com.pdz.team;

import com.pdz.team.dto.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.pdz.team.PositionEnum.*;

public class TeamServer {
    private static final Logger logger = Logger.getLogger(TeamServer.class.getName());

    private final int port;
    private final Server server;

    public TeamServer(int port) throws IOException {
        this(port, TeamUtils.getDefaultFeaturesFile());
    }

    public TeamServer(int port, URL teamFile) throws IOException {
        this(ServerBuilder.forPort(port), port, TeamUtils.parseTeam(teamFile));
    }

    public TeamServer(ServerBuilder<?> serverBuilder, int port, Collection<Team> teams) {
        this.port = port;
        server = serverBuilder.addService(new TeamService(teams)).build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("The server is running and listening port: " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** JVM is shutting down");
                try {
                    TeamServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void blockUntilShutDown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws Exception {
        TeamServer server = new TeamServer(8980);
        server.start();
        server.blockUntilShutDown();
    }

    private static class TeamService extends TeamServiceGrpc.TeamServiceImplBase {
        private final Collection<Team> teams;

        TeamService(Collection<Team> teams) {
            this.teams = teams;
        }

        @Override
        public void sayHello(Member req, StreamObserver<MessageResponse> responseObserver) {
            MessageResponse response = MessageResponse.newBuilder().setMessage("Hello " + req.getPerson().getName()
                    + " congratulation on being a zup " + req.getPosition().getName()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void getTeamByPerson(Person req, StreamObserver<Team> responseObserver) {
            responseObserver.onNext(matchTeamByPerson(req));
            responseObserver.onCompleted();
        }

        @Override
        public void getMembersByPosition(Position req, StreamObserver<Member> responseObserver) {
            for (Team team : teams) {
                for(Member member : team.getMemberList()) {
                    if(member.getPosition().equals(req)) {
                        responseObserver.onNext(member);
                    }
                }
            }
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<Person> estimatePositionsByPersons(final StreamObserver<EstimatePosition> responseObserver){
            return new StreamObserver<Person>() {
                int sreCounter;
                int qaCounter;
                int devCounter;
                int tlCounter;

                @Override
                public void onNext(Person person) {
                    Position position = getPositionByPerson(person);
                    if(DEV.getId() == position.getId()) {
                        devCounter++;
                    } else if(SRE.getId() == position.getId()) {
                        sreCounter++;
                    } else if(QA.getId() == position.getId()) {
                        qaCounter++;
                    } else {
                        tlCounter++;
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.log(Level.WARNING, "estimatePositionsByPersons cancelled");
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(EstimatePosition.newBuilder().setDevCounter(devCounter)
                            .setQaCounter(qaCounter).setTlCounter(tlCounter).setSreCounter(sreCounter).build());
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public StreamObserver<Team> getPersonByTeam(final StreamObserver<Person> responseObserver) {
            return new StreamObserver<Team>() {
                @Override
                public void onNext(Team team) {
                    List<Person> personList = getPositionByTeam(team);
                    for(Person person : personList) {
                        responseObserver.onNext(person);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.log(Level.WARNING, "getPersonTeam cancelled");
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        private Team matchTeamByPerson(Person person) {
            for (Team team : teams) {
                for (Member member : team.getMemberList()) {
                    if (person.getId() == member.getPerson().getId()) {
                        return team;
                    }
                }
            }
            return null;
        }

        private Position getPositionByPerson(Person person) {
            for (Team team : teams) {
                for (Member member : team.getMemberList()) {
                    if (person.getId() == member.getPerson().getId()) {
                        return member.getPosition();
                    }
                }
            }
            return null;
        }

        private List<Person> getPositionByTeam(Team teamRequest) {
            List<Person> positionList = new ArrayList<>();
            for (Team team : teams) {
                if(teamRequest.getId() == team.getId()) {
                    for (Member member : team.getMemberList()) {
                        positionList.add(member.getPerson());
                    }
                }
            }
            return positionList;
        }
    }
}
