package org.firstinspires.ftc.teamcode.robot.subsystems;

import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.MultipleTelemetry;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;

import java.util.concurrent.TimeUnit;

@Config
public class ScoringMech {
    private enum ScoringState {
        RETRACTED,
        DROPPINGV4B,
        EXTENDING,
        GRABBING,
        RETRACTING,
        COLLECTING_1,
        TRANSFERRING,
        RELEASING_1,
        RELEASING_2,
        LOWERED,
        LIFTING,
        CONTROLLING_ARM,
        DEPOSITING,
        LOWERING,
        RESET
    }

    private ScoringState scoringState = ScoringState.RESET;

    private Lift.LiftState previousLiftState = Lift.LiftState.HIGH;

    private boolean controllingArm = false;

    private Lift lift;
    private Intake intake;
    private Rumbler rumbler;

    private MultipleTelemetry telemetry;

    private final ElapsedTime eTime = new ElapsedTime(ElapsedTime.Resolution.SECONDS);

    public static int v4bRetractTime = 1000;

    private boolean previousIntakeGrabButton = false;
    private double previousYawArmAngle = 0;

    public ScoringMech(HardwareMap hardwareMap, Rumbler rumbler, MultipleTelemetry multipleTelemetry) {
        telemetry = multipleTelemetry;
        this.rumbler = rumbler;

        lift = new Lift(hardwareMap, multipleTelemetry);
        intake = new Intake(hardwareMap, multipleTelemetry);
    }

    public void score(boolean intakeGrabButton, boolean liftButtonHigh, boolean liftButtonMid, boolean liftButtonLow,
                      boolean depositButton, double yawArmY, double yawArmX, boolean cancelAutomation,
                      boolean yawArm0, boolean yawArm90, boolean yawArm180, boolean yawArm270) {
        telemetry.addData("Timer", eTime.time());
        telemetry.addData("smState", scoringState);
        telemetry.addData("v4bBusy", intake.isV4BBusy());
        telemetry.update();

        switch (scoringState) {
            case RETRACTED:
                intake.retractPart(Intake.v4bRetractedPos);
                intake.grab();

                if (intakeGrabButton) {
                    intake.setV4bPos(0.9);
                    intake.release();
                    scoringState = ScoringState.EXTENDING;
                }
                break;

            case DROPPINGV4B:
                if (!intake.isV4BBusy()) {
                    intake.release();
                    intake.extendFully();
                    scoringState = ScoringState.EXTENDING;
                }
                break;

            case EXTENDING:
                if (intake.isConeClose()) {
                    intake.setMultiplier(Intake.slowSpeed);
                } else {
                    intake.setMultiplier(1);
                }
                if (intake.isConeDetected()) {
                    rumbler.rumble(500);
                    intake.stopSlides();
                    intake.grab();
                    scoringState = ScoringState.GRABBING;
                }
                /*
                if (!intake.isBusy() && !intake.isConeDetected()) {
                    scoringState = ScoringState.RESET;
                }
                 */
                break;

            case GRABBING:
                if (!intake.isClawBusy()) {
                    intake.retractPart(Intake.v4bRetractedPos);
                    scoringState = ScoringState.RETRACTING;
                    eTime.reset();
                }
                break;

            case RETRACTING:
                if (eTime.time() > 1) {
                    lift.openGrabber();
                    eTime.reset();
                    scoringState = ScoringState.COLLECTING_1;
                }
                break;

            case COLLECTING_1:
                if (eTime.time() > 0.5) {
                    lift.setLiftState(Lift.LiftState.COLLECT);
                    scoringState = ScoringState.TRANSFERRING;
                }
                break;

            case TRANSFERRING:
                if (!lift.isBusy()) {
                    lift.closeGrabber();
                    eTime.reset();
                    scoringState = ScoringState.RELEASING_1;
                }
                break;

            case RELEASING_1:
                if (eTime.time() > 0.5) {
                    intake.release();
                    scoringState = ScoringState.RELEASING_2;
                }
                break;

            case RELEASING_2:
                if (!intake.isClawBusy()) {
                    /*intake.retractFully();*/
                    intake.setV4bPos(Intake.v4bCompletelyRetractedPos);
                    scoringState = ScoringState.LOWERED;
                }
                break;

            case LOWERED:
                if (/*!intake.isBusy() && */!intake.isV4BBusy()) {
                    lift.setLiftState(previousLiftState);
                    rumbler.rumble(1000);
                    scoringState = ScoringState.LIFTING;
                }
                break;

            case LIFTING:
                if (lift.canControlArm()) {
                    controllingArm = true;
                    lift.setYawArmAngle(previousYawArmAngle);
                    scoringState = ScoringState.CONTROLLING_ARM;
                }
                break;

            case CONTROLLING_ARM:
                if (yawArmY != 0 || yawArmX != 0) {
                    lift.setYawArmAngle(Math.atan2(yawArmY, yawArmX));
                }
                if (yawArm0) {
                    lift.setYawArmAngle(0);
                } else if (yawArm90) {
                    lift.setYawArmAngle(90);
                } else if (yawArm180) {
                    lift.setYawArmAngle(180);
                } else if (yawArm270) {
                    lift.setYawArmAngle(270);
                }

                if (!lift.isBusy()) {
                    if (depositButton) {
                        previousYawArmAngle = lift.getYawArmAngle();
                        previousLiftState = lift.getLiftState();
                        controllingArm = false; // give back auto turn before or after deposit is
                                                // finished?

                        lift.openGrabber();
                        eTime.reset();
                        scoringState = ScoringState.DEPOSITING;
                    }
                }
                break;

            case DEPOSITING:
                if (eTime.time() > Lift.waitTime) {
                    lift.closeGrabber();
                    lift.setLiftState(Lift.LiftState.RETRACT);
                    scoringState = ScoringState.LOWERING;
                }
                break;

            case LOWERING:
                if (!lift.isBusy() && !lift.isYawArmBusy()) {
                    scoringState = ScoringState.RETRACTED;
                }
                break;

            case RESET:
                controllingArm = false;
                intake.retractPart(Intake.v4bRetractedPos);
                intake.grab();
                lift.setLiftState(Lift.LiftState.RETRACT);
                lift.closeGrabber();
                scoringState = ScoringState.RETRACTED;
                break;
        }

        if (liftButtonHigh) {
            lift.setLiftState(Lift.LiftState.HIGH);
            previousLiftState = lift.getLiftState();
        } else if (liftButtonMid) {
            lift.setLiftState(Lift.LiftState.MID);
            previousLiftState = lift.getLiftState();
        } else if (liftButtonLow) {
            lift.setLiftState(Lift.LiftState.LOW);
            previousLiftState = lift.getLiftState();
        }

        lift.update();
        intake.update();

        if (cancelAutomation) {
            scoringState = ScoringState.RESET;
        }

        previousIntakeGrabButton = intakeGrabButton;
    }

    public boolean isControllingArm() {
        return controllingArm;
    }
}