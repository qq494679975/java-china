import com.blade.kit.HashidKit;
import com.javachina.kit.Utils;

public class TestMain {

	public static void main(String[] args) {
		HashidKit hashidKit = new HashidKit("this is my salt");
		System.out.println(hashidKit.encode(12345L));
	}

}
