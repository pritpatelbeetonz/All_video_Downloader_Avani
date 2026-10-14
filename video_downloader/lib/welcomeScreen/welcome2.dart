import 'package:get/get.dart';
import 'package:video_player/video_player.dart';

import '../import_all.dart';

class Welcome2 extends StatefulWidget {
  const Welcome2({super.key});

  @override
  State<Welcome2> createState() => _Welcome2State();
}

class _Welcome2State extends State<Welcome2> {

  late VideoPlayerController controller;

  @override
  void initState() {
    super.initState();

    controller = VideoPlayerController.asset(
      "assets/images/intro2_video_fixed.mp4",
    )

      ..initialize().then((_) {
        setState(() {});
        controller.setVolume(0);
        controller.play();
        controller.setLooping(true);
      });
    print("=======> $controller");
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
    children: [
      /// VIDEO BACKGROUND
    Positioned.fill(
        child: controller.value.isInitialized
            ? FittedBox(
          fit: BoxFit.cover,
          child: SizedBox(
            width: controller.value.size.width,
            height: controller.value.size.height,
            child: VideoPlayer(controller),
          ),
        )
            : Container(color: Colors.black),
      ),

      /// GRADIENT OVERLAY
      Positioned.fill(
        child: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                Colors.transparent,
                Color.fromARGB(50, 255, 255, 255),
                Color.fromARGB(150, 255, 255, 255),
                Colors.white,
              ],
              stops: [0.4, 0.6, 0.8, 1],
            ),
          ),
        ),
      ),

      /// TEXT + BUTTONS
      Container(
        margin: const EdgeInsets.only(left: 20, right: 20),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.end,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [

             Text(
              'high_speed_downloader'.tr,
              style: GoogleFonts.ibmPlexSans(
                fontSize: 30,
                fontWeight: FontWeight.bold,
              ),
            ),

            const SizedBox(height: 10),

             Text(
              'download_videos_from_links_easily'.tr,
              style: GoogleFonts.ibmPlexSans(
                fontSize: 18,
                color: Colors.black,
              ),
            ),
             Text('watch_offline_anytime'.tr, style: GoogleFonts.ibmPlexSans(
              fontSize: 18,
              color: Colors.black,
            ),),
            const SizedBox(height: 80),
          ],
        ),
      )
    ],
    );
  }
}