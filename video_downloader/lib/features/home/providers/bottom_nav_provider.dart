import 'package:flutter_riverpod/flutter_riverpod.dart';

class BottomNavNotifier extends StateNotifier<int>{
  BottomNavNotifier() : super(0);

  void changeTab (int index){
    state =index;
  }

}
final bottomNavProvider = StateNotifierProvider<BottomNavNotifier , int>((ref)=>BottomNavNotifier());