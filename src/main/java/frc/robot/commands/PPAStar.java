package frc.robot.commands;

import java.util.List;

import com.pathplanner.lib.PathConstraints;
import com.pathplanner.lib.PathPlanner;
import com.pathplanner.lib.PathPlannerTrajectory;
import com.pathplanner.lib.PathPoint;
import com.pathplanner.lib.commands.PPSwerveControllerCommand;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.aStar.Pathfinding;
import frc.robot.subsystems.DrivetrainSubsystem;
import frc.robot.subsystems.PoseEstimatorSubsystem;

public class PPAStar extends CommandBase {
  private final DrivetrainSubsystem driveSystem;
  private final PoseEstimatorSubsystem poseEstimatorSystem;
  private PPSwerveControllerCommand pathDrivingCommand;

  private final PathConstraints constraints;
  public final double endX;
  public final double endY;
  public final double endAngle;

  public PPAStar(DrivetrainSubsystem d, PoseEstimatorSubsystem p, PathConstraints constraints, double endX, double endY,
      double endAngle) {
    this.driveSystem = d;
    this.poseEstimatorSystem = p;
    this.constraints = constraints;
    this.endX = endX;
    this.endY = endY;
    this.endAngle = endAngle;

    addRequirements(driveSystem, poseEstimatorSystem);
  }

  // ----------------------------------------------------------------------------
  // Per-schedule setup code.
  @Override
  public void initialize() 
  {
    PathPlannerTrajectory trajectory;

    // Due to repeateded passing of specified end angle as a Rotation2d object,
    // allocate one object and reference it.
    Translation2d endTranslationObj = new Translation2d(endX, endY);
    Rotation2d endRotationObj = Rotation2d.fromDegrees(endAngle);

    Pose2d startPose = poseEstimatorSystem.getCurrentPose();

    // Already there.
    if (startPose.getTranslation().getDistance(endTranslationObj) <= Units.inchesToMeters(10))
      return;

    List<Translation2d> internalPoints = Pathfinding.generatePath(startPose.getX(), startPose.getY(), endX, endY);

    // Depending on if internal points are present, make a new array of the other
    // points in the path.
    if (internalPoints.size() >= 2) {
      PathPoint[] thirdPointToEnd = new PathPoint[internalPoints.size() - 1];

      PathPoint secondPoint = new PathPoint(internalPoints.get(0),
          internalPoints.get(1).minus(internalPoints.get(0)).getAngle(), endRotationObj);

      // Iterate through the points returned 
      for (int i = 0; i < internalPoints.size() - 1; i++) {
        double y1 = internalPoints.get(i + 1).getY();
        double x1 = internalPoints.get(i + 1).getX();
        double y0 = internalPoints.get(i).getY();
        double x0 = internalPoints.get(i).getX();
        Rotation2d angleOfTangentLineToXAxis = new Rotation2d(x1 - x0, y1 - y0);

        thirdPointToEnd[i] = new PathPoint(internalPoints.get(i + 1), angleOfTangentLineToXAxis, null);
      }
      thirdPointToEnd[thirdPointToEnd.length - 1] = new PathPoint(endTranslationObj, endRotationObj, endRotationObj);

      trajectory = PathPlanner.generatePath(constraints,
          new PathPoint(startPose.getTranslation(), internalPoints.get(0).minus(startPose.getTranslation()).getAngle(),
              startPose.getRotation()),
          secondPoint,
          thirdPointToEnd);
    } else {
      trajectory = PathPlanner.generatePath(constraints,
          new PathPoint(startPose.getTranslation(), driveSystem.getModulePositions()[0].angle, startPose.getRotation()),
          new PathPoint(endTranslationObj, endRotationObj, endRotationObj));
    }

    pathDrivingCommand = DrivetrainSubsystem.followTrajectory(driveSystem, poseEstimatorSystem, trajectory);
    pathDrivingCommand.schedule();
  }

  @Override
  public boolean isFinished()
  {
    return (pathDrivingCommand == null || !pathDrivingCommand.isScheduled());
  }

  @Override
  public void end(boolean interrupted)
  {
    if (interrupted)
    {
      pathDrivingCommand.cancel();
    }

    driveSystem.stop();
  }
}