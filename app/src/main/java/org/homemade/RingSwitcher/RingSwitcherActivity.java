package org.homemade.RingSwitcher;

import android.app.Activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.os.Bundle;
import android.os.Handler;

import android.util.Log;
import android.util.LruCache;

import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.lang.Runnable;

import java.util.ArrayList;


public class RingSwitcherActivity extends Activity
{
    private float CORRECTION_ANGLE = 180.0f;  // The correction between JAVA's default coordinate to desired coordinate. 
    private float SUDDEN_MOTION_THRESHOLD = 30.0f;  // This is the threshold to prevent RAPID motion on the ring. Tune this if you allow the ring to rotate(move around, actually) faster.
    private int displayWidth;
    private int displayHeight;
    private int child_count;
    private int topPadding = 20;
    private RelativeLayout layoutRing;
    private int ring_centerX, ring_centerY;   // This is the center position for the ring containing the correction of "half size of child"
    private int real_centerX, real_centerY;
    private int pin_centerX, pin_centerY;   // "THE Pin" is the top center corner of the ring !!
    private int currentSelectedChild = 0;  // UPDATE THIS during addView(), rotate_*() !!
    
    private ArrayList<Float> child_polarAngle;
    private ArrayList<View> child_list;
    private int child_size = 32;
    private float angle_gap = 360.0f;
    private boolean isDragging = false;    // This will tell the app if the user is controlling the ring.
    private int child_radius;
    private int sensitive_range = 30;
    private float min_sense_radius;
    private float max_sense_radius;
    private String LOGTAG = new String( "RingSwitcher::" );
    private GestureDetector inputlistener_inst = new GestureDetector( new DragListener() );
    private LruCache<String, Bitmap> mMemoryCache;
    private boolean AUTO_REPOSITION = true;
    // ============================ Supports of Russian Roulette behavior ===================================
    private boolean RUSSIAN_ROULETTE = false;  // Enable this will make the ring to auto.ly pull the INCOMING child to the top center whenever finger up happens.
    private float rr_AngularSpeedThreshold = 0.0f;   // This is the minimum speed to trigger RR behavior.
    private boolean clockwiseOrAnti = true;
    private float lastAngularSpeed = 0.0f;    
    private float angularSpeed = 0.0f;
    private float rrPivotAngle = 0.0f;
    private float angleToTop = 0.0f;
    // ============================ These are the definition of static widgets =============================
    private int ANIMATION_DURATION = 35;  // The period for in-place animation, measured in milli-second    
    private int RING_ANIMATION_DURATION = 35;
    private float pivotAngle = 0.0f;
    private float alphaIter = 0.7f;
    private boolean alphaIncDec = true;
    private ImageView imageviewTop;
    private ImageView imageviewRing;
    private ImageView imageviewOutRing;
    private ImageView imageviewInnerRing;
    private ImageView imageviewCenterButton;
    // =====================================================================================================

    Handler timerAnimationHandler = new Handler(); 
    /**
     * This is the thread for UI periodic(tick/timer-based) animation.
     * Those animation of the widgets who IS NOT affected by control and IS PERIODICAL can be put here!!
     * HAVE BETTER KEEP IT AS SHORT AS POSSIBLE!!
     */
    private Runnable runnable = new Runnable() {
        @Override
        public void run() 
        {
            // do periodic animation here
            // Example: 
            //pivotAngle += 0.08f;   // Minimal angular speed among the widgets is 2 deg/sec. This thread is called by 25 times in 1 second. So for each frame, the angular speed is 2.0/25
            pivotAngle += 0.12f;   // GO FASTER, I WAS TOLD TO DO SO
            if ( pivotAngle >= 360.0f )
                pivotAngle -= 360.0f;

            if ( alphaIter <= 0.7f ) {
                alphaIncDec = true;
            }
            else if ( alphaIter >= 0.99f ) {
                alphaIncDec = false;
            }
            if ( alphaIncDec )
                alphaIter += 0.0075f;
            else
                alphaIter -= 0.0075f;
           
            imageviewOutRing.setPivotX( imageviewOutRing.getWidth()/2 );
            imageviewOutRing.setPivotY( imageviewOutRing.getHeight()/2 );
            imageviewOutRing.setRotation( -pivotAngle*2 );

            imageviewInnerRing.setPivotX( imageviewInnerRing.getWidth()/2 );
            imageviewInnerRing.setPivotY( imageviewInnerRing.getHeight()/2 );
            imageviewInnerRing.setRotation( pivotAngle );

            imageviewCenterButton.setAlpha( alphaIter );
            timerAnimationHandler.postDelayed( this, ANIMATION_DURATION );
        }
    };

    /**
     * This thread will rotate the children further when tap is finished to pull the incoming child into TOP-CENTER of the ring.
     * This should be stopped whenever onTap() happen again to return the control to user!
     */
    private Runnable inertiaRunnable = new Runnable() {
        @Override
        public void run() 
        {
            //timerAnimationHandler.postDelayed( this, ANIMATION_DURATION );
        }
    };

    /**
     * This method scale and set alpha value to those image views or placing other "static"(position-wise)
     * widgets into the place. 
     * ADD INITIALIZATION FOR NEW DECORATION(UNCONTROLLABLE HERE !!!
     */

    private void extra_init()  
    {
        // FOR TEST ONLY
        imageviewTop = (ImageView)findViewById( R.id.imageview_top );
        imageviewTop.setOnTouchListener( new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                Toast.makeText( v.getContext(), "ImageViewTop Tapped!!", Toast.LENGTH_LONG).show();
                return false;
            }
        } );
        // FOR TEST ONLY
        // 2 inner rotating ring:        
        imageviewRing = (ImageView)findViewById( R.id.imageview_ring );
        imageviewOutRing = (ImageView)findViewById( R.id.imageview_out_circle );
        ViewGroup.LayoutParams layoutParams = imageviewOutRing.getLayoutParams();
        //layoutParams.width = child_radius - sensitive_range;
        layoutParams.width = child_radius + child_size;
        layoutParams.height = layoutParams.width;
        imageviewOutRing.setLayoutParams( layoutParams );
        imageviewOutRing.setX( real_centerX - layoutParams.width/2 );
        imageviewOutRing.setY( real_centerY - layoutParams.height/2 );

        ImageView imageviewRingCover = (ImageView)findViewById( R.id.imageview_ring_cover );
        imageviewRingCover.setLayoutParams( layoutParams );
        imageviewRingCover.setX( real_centerX - layoutParams.width/2 );
        imageviewRingCover.setY( real_centerY - layoutParams.height/2 );        

        imageviewInnerRing = (ImageView)findViewById( R.id.imageview_inner_circle );
        imageviewInnerRing.setLayoutParams( layoutParams );
        imageviewInnerRing.setX( real_centerX - layoutParams.width/2 );
        imageviewInnerRing.setY( real_centerY - layoutParams.height/2 );        

        imageviewCenterButton = (ImageView)findViewById( R.id.imageview_center_button );
        ViewGroup.LayoutParams layoutParamsCenterBtn = imageviewCenterButton.getLayoutParams();
        layoutParamsCenterBtn.width = (int)layoutParams.width * 4 / 7;  // FXXK THESE MAGIC NUMBER!!
        layoutParamsCenterBtn.height = layoutParamsCenterBtn.width;
        imageviewCenterButton.setLayoutParams( layoutParamsCenterBtn );
        imageviewCenterButton.setX( real_centerX - layoutParamsCenterBtn.width/2 );
        imageviewCenterButton.setY( real_centerY - layoutParamsCenterBtn.height/2 );        
    }
 
    /**
     *  This method insert the needed bitmap into a LRU cache.
     *  Add yours into the array with its resource ID.
     */
    private void cache_init()
    {
        int res_array[] = { 
            R.drawable.setting_bg_dotbg_normal,
            R.drawable.setting_btn_dot_normal,
            R.drawable.setting_move_dotcirclein_normal,
            R.drawable.setting_move_dotcircleout_normal,
            R.drawable.snapshot_bg_dotbg_normal,
            R.drawable.snapshot_btn_dot_normal,
            R.drawable.snapshot_move_dotcirclein_normal,
            R.drawable.snapshot_move_dotcircleout_normal,
            R.drawable.rec_bg_dotbg_normal,
            R.drawable.rec_btn_dot_normal,
            R.drawable.rec_move_dotcirclein_normal,
            R.drawable.rec_move_dotcircleout_normal,
            R.drawable.playback_bg_dotbg_normal,
            R.drawable.playback_btn_dot_normal,
            R.drawable.playback_move_dotcirclein_normal,
            R.drawable.playback_move_dotcircleout_normal
        };
        
        for ( int i = 0; i < res_array.length; i++ ) {
            addBitmapToMemoryCache( Integer.toString( res_array[i] ), 
                                    BitmapFactory.decodeResource( this.getResources(), res_array[i] ) );
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.main );
        // ===================== Allocating a cache for bitmap ============================
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 10;  // This should be reasonably small, usually set at 1.6MB
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        // ================================================================================
        layoutRing = (RelativeLayout)this.findViewById( R.id.layout_ring );
        
        child_count = 0;
        child_radius = ( layoutRing.getHeight() / 2 ) - topPadding;
        child_list = new ArrayList<View>();
        child_polarAngle = new ArrayList<Float>();        
        View v = this.findViewById( R.id.imageview_top );
        
        ImageView image0 = new ImageView( RingSwitcherActivity.this );
        image0.setImageResource( R.drawable.comm_icon_rec_normal );
        ViewGroup.LayoutParams imageLP = new ViewGroup.LayoutParams( child_size, child_size );
        image0.setLayoutParams( imageLP );
        ImageView image1 = new ImageView( RingSwitcherActivity.this );
        image1.setImageResource( R.drawable.comm_icon_setting_normal );
        image1.setLayoutParams( imageLP );
        ImageView image2 = new ImageView( RingSwitcherActivity.this );
        image2.setImageResource( R.drawable.comm_icon_snapshot_normal );
        image2.setLayoutParams( imageLP );
        ImageView image3 = new ImageView( RingSwitcherActivity.this );
        image3.setImageResource( R.drawable.comm_icon_playback_normal );
        image3.setLayoutParams( imageLP );
        
        addView( image0 );
        addView( image1 );
        addView( image2 );
        addView( image3 );
        cache_init();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) 
    {
        // TODO Auto-generated method stub
        super.onWindowFocusChanged(hasFocus);
        //Here you can get the size!
        real_centerX = layoutRing.getWidth() / 2;
        real_centerY = layoutRing.getHeight() / 2;
        ring_centerX = ( real_centerX - child_size / 2 );
        ring_centerY = ( real_centerY - child_size / 2 );
        pin_centerX = ring_centerX;
        pin_centerY = ring_centerY - child_radius;
        child_radius = ( layoutRing.getHeight() / 2 ) - topPadding;

        extra_init();

        relocateAll();
        rotate_clockwise( -child_polarAngle.get( 0 ) );
        layoutRing.setOnTouchListener( new TapPosReader() );
        timerAnimationHandler.postDelayed( runnable, 40 );        
        //setChild( 1, 3.0f, false, false );
    }
    
    /**
     *  Add a new child through this method. The children will be distributed on the ring automatically.
     * \param child_view The new child to be added
     */
    public void addView( View child_view ) 
    {
        //(ViewGroup)( child_view.getParent() ).removeView( child_view );
        child_list.add( child_view );
        layoutRing.addView( child_view );
        child_view.setVisibility( View.VISIBLE );
        child_count++;
        angle_gap = 360.0f / child_count;
        float new_pa = angle_gap * ( child_list.size() -1 ); 
        child_polarAngle.add( new_pa );
        relocateAll(); // update the child
    }

    /**
     *  This method rotate the children ring to make the child[index] to the top. The control will be DISABLED during this method.
     *  \param index The number of the child who is going to be the top.
     *  \param angularSpeed The angular speed could be set(measured in degree per frame.) But don't make it too slow to irritate the user.
     *  \param inertiaBehavior This will make the ring rotate with the same direction and speed as last onScroll()
     */
    public void setChild( int index, float in_angularSpeed, boolean inertiaBehavior ) 
    {
        // Stop listening gesture.
        layoutRing.setOnTouchListener( null );
        if ( child_polarAngle.get( index ) > 180.0f ) {
            angleToTop = 360.0f - child_polarAngle.get( index ); // going to be >0
            angularSpeed = -Math.abs( in_angularSpeed );
        }
        else {
            angleToTop = 0.0f - child_polarAngle.get( index );  // going to be <= 0
            angularSpeed = Math.abs( in_angularSpeed );
        }
        // CLEAR YOUR MIND!!
        // update angleToTop and angularSpeed in proper manner
        childRotationHandler.postDelayed( childToTopRunnable, RING_ANIMATION_DURATION );
    }

    Handler childRotationHandler = new Handler();
    Runnable childToTopRunnable = new Runnable() {
        public void run() 
        {
            // angleToTop count down to zero and rotate the ring
            rotate_clockwise( angularSpeed );
            // update angleToTop
            angleToTop += angularSpeed;
            if ( Math.abs( angleToTop ) > Math.abs(angularSpeed) ) {
                //rotate_clockwise( angleToTop );
                childRotationHandler.postDelayed( this, RING_ANIMATION_DURATION );
            }
            else {
                rotate_clockwise( -angleToTop );
                angleToTop = 0;
                layoutRing.setOnTouchListener( new TapPosReader() );
            }
        }
    };
    
    /**
     *  Add other statement when the selected child is changed.
     * \param prev_selected The No. of previous selected child.
     * \param cur_selected The No. of just-selected child.
     */
    private void onChangeSelectedChild( int prev_selected, int cur_selected )
    {
        switch ( cur_selected ) {
        case 0:
            imageviewRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.rec_bg_dotbg_normal ) ) );
            imageviewOutRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.rec_move_dotcircleout_normal ) ) );
            imageviewInnerRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.rec_move_dotcirclein_normal ) ) );
            imageviewCenterButton.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.rec_btn_dot_normal ) ) );
            break;
        case 1:
            // key form: Integer.toString( res_array[i] )
            imageviewRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.setting_bg_dotbg_normal ) ) );
            imageviewOutRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.setting_move_dotcircleout_normal ) ) );
            imageviewInnerRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.setting_move_dotcirclein_normal ) ) );
            imageviewCenterButton.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.setting_btn_dot_normal ) ) );
            break;
        case 2:
            imageviewRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.snapshot_bg_dotbg_normal ) ) );
            imageviewOutRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.snapshot_move_dotcircleout_normal ) ) );
            imageviewInnerRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.snapshot_move_dotcirclein_normal ) ) );
            imageviewCenterButton.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.snapshot_btn_dot_normal ) ) );
            break;
        case 3:
            imageviewRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.playback_bg_dotbg_normal ) ) );
            imageviewOutRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.playback_move_dotcircleout_normal ) ) );
            imageviewInnerRing.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.playback_move_dotcirclein_normal ) ) );
            imageviewCenterButton.setImageBitmap( getBitmapFromMemCache( Integer.toString( R.drawable.playback_btn_dot_normal ) ) );
            break;

        }
                
    }

    /**
     * This shall be invoked whenever a view is added or removed to adjust the new angle for every child
     * (except the latest added/removed.)
     */
    private void relocateAll()
    {
        float newangle = 0.0f;
        for ( int i = 0; i< child_polarAngle.size(); i++ ) {
            newangle = ( 360.0f / child_count ) * i;
            //child_view.get(i).setVisibility( View.VISIBILE );
            //child_polarAngle.get(i) = new Float( ( 360.0f / child_count ) * i );
            child_polarAngle.set( i, newangle );
            //placeChild( i );
            rotateChild( i, 0.0f );
        }
    }
    
    /**
     * Make sure the child is added to the ring layout before invoking this...
     */
    private void placeChild( int index )
    {
        int leftPadding = ( ring_centerX + (int)( child_radius * Math.sin( Math.toRadians( index * angle_gap ) ) ) );
        //int topPadding = ( layoutRing.getHeight()/2 + (int)( child_radius*Math.sin( index * angle_gap ) ) );
        int topPadding = ( ring_centerY - (int)( child_radius * Math.cos( Math.toRadians( index * angle_gap ) ) ) );
        //int topPadding = ( layoutRing.getHeight() / 2 + radius );
        child_list.get(index).setTranslationX( leftPadding );
        child_list.get(index).setTranslationY( topPadding );
    }

    /**
     * Update the polar angle of child and redraw its position.
     * \param index The child index.
     */
    private void rotateChild( int index, float var_degree )
    {        
        child_polarAngle.set( index, child_polarAngle.get(index) + var_degree );
        if ( child_polarAngle.get(index) > 360.0f )  {
            child_polarAngle.set( index, child_polarAngle.get(index) - 360.0f );
        }
        else if ( child_polarAngle.get(index) < 0.0f ) {
            child_polarAngle.set( index, child_polarAngle.get(index) + 360.0f );
        }

        float newX = ring_centerX + child_radius * (float)Math.sin( Math.toRadians( child_polarAngle.get(index) + CORRECTION_ANGLE ) );
        float newY = ring_centerY + child_radius * (float)Math.cos( Math.toRadians( child_polarAngle.get(index) + CORRECTION_ANGLE ) );

        child_list.get(index).setX( newX );
        child_list.get(index).setY( newY );
    }

    /**
     * This will TRANSLATE the child on the ring along the ring like they're rotating...
     * (while they're actually CIRCLING the center of the ring.)
     * MAKE SURE EVERY TRANSLATION OF CHILDREN GO THROUGH THIS METHOD!!
     * \param var_degree The angle to /rotate/ the children...
     */
    public void rotate_clockwise( float var_degree ) 
    {
        for ( int i = 0; i < child_list.size(); i++ )
        {
            rotateChild( i, 0.0f - var_degree );  // YEAH, I made stupid mistake!! The clockwise is actually minusdegree for Java.
            // ======================= check if i is nearer to the TOP PIN of roulette ========================
            if ( i == ( currentSelectedChild + 1 ) % child_list.size() ||
                 i == ( currentSelectedChild+child_list.size() - 1 ) % child_list.size() ) {
                float cscToZeroDiff = ( child_polarAngle.get( currentSelectedChild ) <= 180.0f ) ?
                    child_polarAngle.get( currentSelectedChild ) :
                    360.0f - child_polarAngle.get( currentSelectedChild );
                float iToZeroDiff = ( child_polarAngle.get( i ) <= 180.0f ) ? 
                    child_polarAngle.get( i ) :
                    360.0f - child_polarAngle.get( i );

                if ( iToZeroDiff < cscToZeroDiff ) {  // if the angle between pin and child is smaller than pin and C.S.C., then ...
                    onChangeSelectedChild( currentSelectedChild, i );  
                    currentSelectedChild = i;
                }
            }
        }
    }
    
    public void rotate_anticlockwise( float degree)
    {
        rotate_clockwise( 0.0f - degree );
    }

    //private double dragAngle( Point current, Point previous ) 
    private double dragAngle( int prevX, int prevY, int curX, int curY ) 
    {
        //int real_centerX = layoutRing.getWidth() / 2;
        //int real_centerY = layoutRing.getHeight() / 2;

        return -Math.toDegrees( Math.atan2( curX - real_centerX, curY - real_centerY ) -
                                Math.atan2( prevX- real_centerX, prevY - real_centerY ) );
    }
    
    /**
     * dragVectorToDegree decides how large is the angle variable between each onScroll() event by the 
     */
    // This likely to get curX, curY from onFling()
    private double dragVectorToDegree( int P1_x, int P1_y, int P2_x, int P2_y )
    {
        int deltaY = P2_y - P1_y;
        int deltaX = P2_x - P1_x;        
        int real_centerX = layoutRing.getWidth() / 2;
        int real_centerY = layoutRing.getHeight() / 2;
        
        double raw = Math.atan2( deltaY, deltaX ) * 180.0d / 3.14159;
        
        if ( P1_x > real_centerX ) // correct for I,IV
            return raw;  
        else
            return 0.0d - raw;    // Invert for II,III
    }
    
    // count by distance
    private boolean in_range( int curX, int curY )
    {
        int real_centerX = layoutRing.getWidth() / 2;
        int real_centerY = layoutRing.getHeight() / 2;

        double dist = Math.pow( ( curX - real_centerX ), 2.0f ) +
            Math.pow( ( curY - real_centerY ), 2.0f );
        if ( dist < Math.pow( ( child_radius + sensitive_range ), 2.0f ) && 
             dist > Math.pow( ( child_radius - sensitive_range ), 2.0f ) ) 
            return true;
        else 
            return false;
    }

    public void addBitmapToMemoryCache(String key, Bitmap bitmap) 
    {
        if ( getBitmapFromMemCache(key) == null ) {
            mMemoryCache.put(key, bitmap);
        }
    }
    
    public Bitmap getBitmapFromMemCache(String key) 
    {
        return mMemoryCache.get(key);
    }

    /** 
     * The touch controller related:
     */
    public class DragListener extends SimpleOnGestureListener 
    {
        int prevX = 0;
        int prevY = 0;
        int viewWidth = 0;
        int xVarOnUp = 0;
        int yVarOnUp = 0;
        int xVarAccum = 0;
        int yVarAccum = 0;
        /**
         * determine the user drag:
         * 1. in the control area or not.
         */
        @Override
        public boolean onFling ( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY ) {
            int initX = (int)e1.getX();
            int initY = (int)e1.getY();
            int curX = (int)e2.getX();
            int curY = (int)e2.getY();
                    
            xVarOnUp = curX - initX;
            yVarOnUp = curY - initY;
            
            // Find the smallest/largest according to value of clockwiseOrAnti.
            float minAngle = 360.0f;
            if ( clockwiseOrAnti ) {
                Log.v( "onFling()::", "CLOCKWISE" );
                // By current 
            }
            else {
                Log.v( "onFling()::", "ANTICLOCKWISE" );
            }

            if ( RUSSIAN_ROULETTE ) {
                int incoming;
                if ( clockwiseOrAnti ) {
                    incoming = nextChild();
                    Log.v( "RR::", "CSC=" + currentSelectedChild + ", incoming=" + incoming );
                }
                else {
                    incoming = prevChild();
                    Log.v( "RR::", "CSC=" + currentSelectedChild + ", incoming=" + incoming );
                }
                if ( Math.abs( lastAngularSpeed ) > rr_AngularSpeedThreshold )
                    setChild( incoming, lastAngularSpeed, true );  // Last 'true' tell setChild to do RR behavior
            }
            else if ( AUTO_REPOSITION ) {  // DEFAULT: auto reposition to pin for currenSelectedChild
                setChild( currentSelectedChild, lastAngularSpeed, false );
            }
            return false;
        }

        private int nextChild() 
        {
            return ( currentSelectedChild + 1 ) % child_list.size();
        }
        
        private int prevChild()
        {
            return ( child_list.size() + currentSelectedChild - 1 ) % child_list.size();
        }

        @Override
        public boolean onScroll( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY ) {
            int initX = (int)e1.getX();
            int initY = (int)e1.getY();
            int curX = (int)e2.getX();
            int curY = (int)e2.getY();
            if ( !in_range( curX, curY ) ) {
                return false;
            }            

            int xVarOnScroll = curX - prevX;
            int yVarOnScroll = curY - prevY;
            
            if ( prevX == 0 )
                prevX = curX;
            if ( prevY == 0 )
                prevY = curY;
            if ( viewWidth == 0 )
                viewWidth = layoutRing.getWidth();

            double angle = dragAngle( prevX, prevY, curX, curY );
            lastAngularSpeed = (float)angle;
            // This examines whether the motion is valid to update the related variables.
            if ( Math.abs( angle ) < SUDDEN_MOTION_THRESHOLD ) {   // if this is TOO RAPID TO BE TRUE...
                rotate_clockwise( (float)angle );
                if ( angle > 0.0d ) 
                    clockwiseOrAnti = true;
                else 
                    clockwiseOrAnti = false;
            }            
            prevX = curX;
            prevY = curY;
            return false;
        }
         
        public boolean onSingleTapConfirmed (MotionEvent e)
        {
            int curX = (int)e.getX();
            int incoming;
            if ( curX >= real_centerX ) {
                //incoming = ( child_list.size() + currentSelectedChild - 1 ) % child_list.size();
                incoming = prevChild();
                //incoming = ( currentSelectedChild + 1 ) % child_list.size() ;
            }
            else {
                //incoming = ( currentSelectedChild + 1 ) % child_list.size() ;
                incoming = nextChild();
                //incoming = ( child_list.size() + currentSelectedChild - 1 ) % child_list.size();
            }
            Log.v( "SingleTap::", "rotate to: " + incoming );
            setChild( incoming, 9.0f, false );
            //==================================================
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) 
        {
            return true;
        }
    }

    public class TapPosReader implements OnTouchListener 
    {
        @Override
        public boolean onTouch( View view, MotionEvent event ) 
        {
            int x = (int)event.getRawX();
            int y = (int)event.getRawY();
            Log.d( "TapPos::", " ( " + x + ", " + y + " )" );
            return inputlistener_inst.onTouchEvent(event);            
            //return true;
        }
    }
    
    public class Point 
    {
        public int x;
        public int y;

        public Point( int in_x, int in_y )
        {
            x = in_x; y = in_y;
        }
    }
}
