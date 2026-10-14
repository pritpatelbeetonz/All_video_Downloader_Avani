import 'package:get/get.dart';
import '../import_all.dart';

class Welcome1 extends StatelessWidget {
  const Welcome1({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [

        /// Flexible image so it shrinks if needed
        Expanded(
          child: Image.asset(
            "assets/images/intro1_image.png",
            width: double.infinity,
            fit: BoxFit.cover,
          ),
        ),

        Container(
          margin: const EdgeInsets.only(left: 20, right: 20),
          child:  Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [

              Text(
                'video_downloader'.tr,
                style: GoogleFonts.ibmPlexSans(
                  fontSize: 30,
                  fontWeight: FontWeight.bold,
                ),
              ),

              SizedBox(height: 6),

              Text(
                'download_videos_instantly'.tr,
                style: GoogleFonts.ibmPlexSans(
                  fontSize: 18,
                  color: Colors.black,
                ),
              ),

              Text(
                'using_any_link'.tr,
                style: GoogleFonts.ibmPlexSans(
                  fontSize: 18,
                  color: Colors.black,
                ),
              ),

              SizedBox(height: 80), // space before bottom nav
            ],
          ),
        ),
      ],
    );
  }
}